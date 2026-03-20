package com.banner.logs.viewmodel

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
    val maxLines: Int = 2000,
    val fontSize: Float = 12f,
    val autoScroll: Boolean = true,
    val showTimestamp: Boolean = true,
    val showPidTid: Boolean = false,
    val wrapLines: Boolean = false,
    val totalEntries: Int = 0,
    val shownEntries: Int = 0,
    val exportMessage: String? = null
)

class LogViewModel(application: Application) : AndroidViewModel(application) {

    private val entriesMutex = Mutex()
    private val pidsMutex = Mutex()
    private val allEntries = ArrayDeque<LogEntry>()
    private val pidMap = mutableMapOf<Int, String>()

    private val _filteredEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val filteredEntries: StateFlow<List<LogEntry>> = _filteredEntries.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var logcatJob: Job? = null
    private var pidRefreshJob: Job? = null
    private val dirty = AtomicBoolean(false)

    init {
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
                    while (allEntries.size > max) allEntries.removeFirst()
                }
                dirty.set(true)
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

    fun resume() {
        start()
    }

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

    fun exportLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                val fileName = "banner-logs-${sdf.format(Date())}.txt"
                val content = entriesMutex.withLock { allEntries.joinToString("\n") { it.raw } }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val ctx = getApplication<Application>()
                    val uri = ctx.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    )
                    if (uri != null) {
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        ctx.contentResolver.update(uri, values, null, null)
                        _uiState.update { it.copy(exportMessage = "Saved to Downloads/$fileName") }
                    } else {
                        _uiState.update { it.copy(exportMessage = "Export failed: could not create file") }
                    }
                } else {
                    // API 26-28: write via root to avoid permission requirement
                    val cacheFile = File(getApplication<Application>().cacheDir, fileName)
                    cacheFile.writeText(content)
                    val proc = Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "cp '${cacheFile.absolutePath}' '/sdcard/Download/$fileName'")
                    )
                    val exit = proc.waitFor()
                    cacheFile.delete()
                    if (exit == 0) {
                        _uiState.update { it.copy(exportMessage = "Saved to /sdcard/Download/$fileName") }
                    } else {
                        _uiState.update { it.copy(exportMessage = "Export failed") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportMessage = "Export failed: ${e.message}") }
            }
        }
    }

    private suspend fun refreshPidMap() {
        val map = LogcatReader.getPidMap()
        pidsMutex.withLock {
            pidMap.clear()
            pidMap.putAll(map)
        }
    }

    private suspend fun recomputeFiltered() {
        val filter = _uiState.value.filterState
        val snapshot = entriesMutex.withLock { allEntries.toList() }
        val pids = pidsMutex.withLock { pidMap.toMap() }

        val filtered = if (filter.searchText.isEmpty() &&
            filter.packageFilter.isEmpty() &&
            filter.enabledLevels.size == (LogLevel.entries.size - 1)) {
            snapshot // fast path: no filters active
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
