package com.banner.logs.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.banner.logs.data.LogEntry
import com.banner.logs.data.LogLevel
import com.banner.logs.reader.LogcatReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class FilterState(
    val searchText: String = "",
    val packageFilter: String = "",
    val enabledLevels: Set<LogLevel> = (LogLevel.entries.toSet() - LogLevel.UNKNOWN)
)

data class UiState(
    val isRootAvailable: Boolean? = null,   // null = checking
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val filterState: FilterState = FilterState(),
    /** Saved package filter presets. */
    val savedFilters: List<String> = emptyList(),
    /** 0 = unlimited */
    val maxLines: Int = 0,
    val fontSize: Float = 12f,
    val autoScroll: Boolean = true,
    val showTimestamp: Boolean = true,
    val showPidTid: Boolean = false,
    val wrapLines: Boolean = false,
    val totalEntries: Int = 0,
    val shownEntries: Int = 0,
    val exportMessage: String? = null,
    val liveFileEnabled: Boolean = false,
    val liveFilePath: String = "",
    val exportUriString: String = "",
    val exportPathDisplay: String = ""
)

private const val PREFS_NAME = "simple_logcat_prefs"
private const val KEY_SAVED_FILTERS = "saved_package_filters"
private const val KEY_EXPORT_URI = "export_uri"

class LogViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val entriesMutex = Mutex()
    private val allEntries = ArrayDeque<LogEntry>()
    private val pidMap = ConcurrentHashMap<Int, String>()

    private val _filteredEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val filteredEntries: StateFlow<List<LogEntry>> = _filteredEntries.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var logcatJob: Job? = null
    private var pidRefreshJob: Job? = null
    private val dirty = AtomicBoolean(false)

    init {
        // Load saved package filter list
        val raw = prefs.getString(KEY_SAVED_FILTERS, "") ?: ""
        val savedFilters = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val exportUriString = prefs.getString(KEY_EXPORT_URI, "") ?: ""
        val exportPathDisplay = if (exportUriString.isNotEmpty())
            uriToDisplayPath(Uri.parse(exportUriString)) else ""
        _uiState.update { it.copy(
            savedFilters = savedFilters,
            exportUriString = exportUriString,
            exportPathDisplay = exportPathDisplay
        ) }

        startDirtyChecker()
        viewModelScope.launch(Dispatchers.IO) {
            val hasRoot = LogcatReader.checkRoot()
            _uiState.update { it.copy(isRootAvailable = hasRoot) }
            if (hasRoot) {
                refreshPidMap()
                start()
            }
        }
    }

    private fun startDirtyChecker() {
        viewModelScope.launch {
            while (true) {
                delay(200)
                if (dirty.getAndSet(false)) {
                    recomputeFiltered()
                }
            }
        }
    }

    fun start() {
        if (logcatJob?.isActive == true) return
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            LogcatReader.stream().collect { entry ->
                entriesMutex.withLock {
                    allEntries.addLast(entry)
                    val max = _uiState.value.maxLines
                    if (max > 0) {
                        while (allEntries.size > max) allEntries.removeFirst()
                    }
                    // max == 0 means unlimited — no trimming
                }
                dirty.set(true)

                val state = _uiState.value
                if (state.liveFileEnabled && state.liveFilePath.isNotEmpty()) {
                    val pkgFilter = state.filterState.packageFilter
                    val levelOk = entry.level in state.filterState.enabledLevels
                    val pkgOk = pkgFilter.isEmpty() ||
                        pidMap[entry.pid]?.contains(pkgFilter, ignoreCase = true) == true ||
                        entry.tag.contains(pkgFilter, ignoreCase = true)
                    if (levelOk && pkgOk) {
                        try { File(state.liveFilePath).appendText(entry.raw + "\n") }
                        catch (e: Exception) { /* ignore write errors */ }
                    }
                }
            }
        }
        pidRefreshJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                refreshPidMap()
            }
        }
        _uiState.update { it.copy(isRunning = true, isPaused = false) }
    }

    fun pause() {
        logcatJob?.cancel(); logcatJob = null
        pidRefreshJob?.cancel(); pidRefreshJob = null
        _uiState.update { it.copy(isRunning = false, isPaused = true) }
    }

    fun resume() { start() }

    fun clearLogs() {
        viewModelScope.launch {
            entriesMutex.withLock { allEntries.clear() }
            _filteredEntries.value = emptyList()
            _uiState.update { it.copy(totalEntries = 0, shownEntries = 0) }
        }
    }

    fun retryRoot() {
        _uiState.update { it.copy(isRootAvailable = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val hasRoot = LogcatReader.checkRoot()
            _uiState.update { it.copy(isRootAvailable = hasRoot) }
            if (hasRoot) {
                refreshPidMap()
                start()
            }
        }
    }

    fun setSearchText(text: String) {
        _uiState.update { it.copy(filterState = it.filterState.copy(searchText = text)) }
        viewModelScope.launch { recomputeFiltered() }
    }

    fun setPackageFilter(pkg: String) {
        _uiState.update { it.copy(filterState = it.filterState.copy(packageFilter = pkg)) }
        viewModelScope.launch { recomputeFiltered() }
    }

    /** Adds the current package filter text to the saved list (no-op if blank or already saved). */
    fun addSavedFilter() {
        val pkg = _uiState.value.filterState.packageFilter.trim()
        if (pkg.isEmpty()) return
        val current = _uiState.value.savedFilters
        if (current.contains(pkg)) return
        val updated = current + pkg
        persistFilters(updated)
        _uiState.update { it.copy(savedFilters = updated) }
    }

    /** Removes a specific entry from the saved filter list. */
    fun removeSavedFilter(pkg: String) {
        val updated = _uiState.value.savedFilters.filter { it != pkg }
        persistFilters(updated)
        _uiState.update { it.copy(savedFilters = updated) }
    }

    private fun persistFilters(list: List<String>) {
        prefs.edit().putString(KEY_SAVED_FILTERS, list.joinToString(",")).apply()
    }

    fun toggleLevel(level: LogLevel) {
        val current = _uiState.value.filterState.enabledLevels
        val updated = if (level in current) current - level else current + level
        _uiState.update { it.copy(filterState = it.filterState.copy(enabledLevels = updated)) }
        viewModelScope.launch { recomputeFiltered() }
    }

    fun setMaxLines(lines: Int) { _uiState.update { it.copy(maxLines = lines) } }
    fun setFontSize(size: Float) { _uiState.update { it.copy(fontSize = size) } }
    fun setAutoScroll(v: Boolean) { _uiState.update { it.copy(autoScroll = v) } }
    fun setShowTimestamp(v: Boolean) { _uiState.update { it.copy(showTimestamp = v) } }
    fun setShowPidTid(v: Boolean) { _uiState.update { it.copy(showPidTid = v) } }
    fun setWrapLines(v: Boolean) { _uiState.update { it.copy(wrapLines = v) } }
    fun dismissExportMessage() { _uiState.update { it.copy(exportMessage = null) } }

    fun toggleLiveFile(context: Context) {
        val current = _uiState.value.liveFileEnabled
        if (current) {
            context.stopService(Intent(context, com.banner.logs.LogcatService::class.java))
            _uiState.update { it.copy(liveFileEnabled = false, liveFilePath = "") }
        } else {
            val file = context.getExternalFilesDir(null)?.resolve("simple-logcat-live.log") ?: return
            file.parentFile?.mkdirs()
            file.writeText("") // clear on each start
            val path = file.absolutePath
            val pkg = _uiState.value.filterState.packageFilter
            val intent = Intent(context, com.banner.logs.LogcatService::class.java).apply {
                putExtra(com.banner.logs.LogcatService.EXTRA_FILE_PATH, path)
                putExtra(com.banner.logs.LogcatService.EXTRA_PACKAGE, pkg)
            }
            context.startForegroundService(intent)
            _uiState.update { it.copy(liveFileEnabled = true, liveFilePath = path) }
        }
    }

    fun exportLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                val fileName = "logcat-${sdf.format(Date())}.txt"
                val content = entriesMutex.withLock { allEntries.joinToString("\n") { it.raw } }

                val uriString = _uiState.value.exportUriString
                if (uriString.isNotEmpty()) {
                    val ctx = getApplication<Application>()
                    val treeUri = Uri.parse(uriString)
                    val docId = DocumentsContract.getTreeDocumentId(treeUri)
                    val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val fileUri = DocumentsContract.createDocument(
                        ctx.contentResolver, dirUri, "text/plain", fileName
                    )
                    if (fileUri != null) {
                        ctx.contentResolver.openOutputStream(fileUri)?.use {
                            it.write(content.toByteArray())
                        }
                        _uiState.update { it.copy(exportMessage = "Saved to ${it.exportPathDisplay}/$fileName") }
                    } else {
                        _uiState.update { it.copy(exportMessage = "Export failed: could not create file") }
                    }
                } else {
                    val cacheFile = File(getApplication<Application>().cacheDir, fileName)
                    cacheFile.writeText(content)
                    val dest = "/sdcard/$fileName"
                    val proc = Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "cp '${cacheFile.absolutePath}' '$dest'")
                    )
                    val exit = proc.waitFor()
                    cacheFile.delete()
                    if (exit == 0) {
                        _uiState.update { it.copy(exportMessage = "Saved to $dest") }
                    } else {
                        _uiState.update { it.copy(exportMessage = "Export failed") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun setExportUri(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val display = uriToDisplayPath(uri)
        prefs.edit().putString(KEY_EXPORT_URI, uri.toString()).apply()
        _uiState.update { it.copy(exportUriString = uri.toString(), exportPathDisplay = display) }
    }

    fun clearExportUri(context: Context) {
        val uriString = _uiState.value.exportUriString
        if (uriString.isNotEmpty()) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { /* ignore */ }
        }
        prefs.edit().remove(KEY_EXPORT_URI).apply()
        _uiState.update { it.copy(exportUriString = "", exportPathDisplay = "") }
    }

    private fun uriToDisplayPath(uri: Uri): String {
        val segment = uri.lastPathSegment ?: return uri.toString()
        return when {
            segment.startsWith("primary:") -> "/sdcard/" + segment.removePrefix("primary:")
            segment.contains(":") -> segment.substringAfter(":")
            else -> segment
        }
    }

    private fun refreshPidMap() {
        val map = LogcatReader.getPidMap()
        pidMap.clear()
        pidMap.putAll(map)
    }

    private suspend fun recomputeFiltered() {
        val filter = _uiState.value.filterState
        val snapshot = entriesMutex.withLock { allEntries.toList() }
        val pids = pidMap.toMap()

        val filtered = if (filter.searchText.isEmpty() &&
            filter.packageFilter.isEmpty() &&
            filter.enabledLevels.size == (LogLevel.entries.size - 1)) {
            snapshot
        } else {
            snapshot.filter { entry ->
                entry.level in filter.enabledLevels &&
                (filter.searchText.isEmpty() ||
                    entry.tag.contains(filter.searchText, ignoreCase = true) ||
                    entry.message.contains(filter.searchText, ignoreCase = true)) &&
                (filter.packageFilter.isEmpty() ||
                    pids[entry.pid]?.contains(filter.packageFilter, ignoreCase = true) == true ||
                    entry.tag.contains(filter.packageFilter, ignoreCase = true))
            }
        }

        _filteredEntries.value = filtered
        _uiState.update { it.copy(totalEntries = snapshot.size, shownEntries = filtered.size) }
    }

    override fun onCleared() {
        super.onCleared()
        logcatJob?.cancel()
        pidRefreshJob?.cancel()
    }
}
