package com.banner.logs.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banner.logs.data.LogEntry
import com.banner.logs.data.LogLevel
import com.banner.logs.data.color
import com.banner.logs.data.rowBackground
import com.banner.logs.viewmodel.LogViewModel
import com.banner.logs.viewmodel.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: LogViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showLevelFilter by remember { mutableStateOf(true) }
    var showPackageFilter by remember { mutableStateOf(
        // auto-open package filter bar if a saved filter was loaded
        false
    ) }

    // Auto-open package filter bar if a saved filter was restored on launch
    LaunchedEffect(Unit) {
        if (uiState.savedPackageFilter.isNotEmpty()) showPackageFilter = true
    }

    // Auto-scroll
    LaunchedEffect(filteredEntries.size) {
        if (uiState.autoScroll && filteredEntries.isNotEmpty()) {
            listState.scrollToItem(filteredEntries.lastIndex)
        }
    }

    // Export snackbar
    LaunchedEffect(uiState.exportMessage) {
        uiState.exportMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.dismissExportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Status dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = when {
                                            uiState.isRootAvailable == null -> Color(0xFFFFB74D)
                                            uiState.isRootAvailable == false -> Color(0xFFEF5350)
                                            uiState.isRunning -> Color(0xFF81C784)
                                            uiState.isPaused -> Color(0xFFFFB74D)
                                            else -> Color(0xFF757575)
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Simple Logcat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        uiState.isRootAvailable == null -> "Checking root..."
                                        uiState.isRootAvailable == false -> "No root access"
                                        uiState.totalEntries > 0 ->
                                            "${uiState.shownEntries} / ${uiState.totalEntries} lines"
                                        else -> "Ready"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Search toggle
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) viewModel.setSearchText("")
                        }) {
                            Icon(
                                if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (showSearch || uiState.filterState.searchText.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else LocalContentColor.current
                            )
                        }
                        // Package filter toggle
                        IconButton(onClick = {
                            showPackageFilter = !showPackageFilter
                            if (!showPackageFilter) viewModel.setPackageFilter("")
                        }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Package filter",
                                tint = if (showPackageFilter || uiState.filterState.packageFilter.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else LocalContentColor.current
                            )
                        }
                        // Level filter toggle
                        IconButton(onClick = { showLevelFilter = !showLevelFilter }) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Level filter",
                                tint = if (uiState.filterState.enabledLevels.size < (LogLevel.entries.size - 1))
                                    MaterialTheme.colorScheme.primary
                                else LocalContentColor.current
                            )
                        }
                        // Pause / Resume
                        if (uiState.isRunning) {
                            IconButton(onClick = { viewModel.pause() }) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.resume() },
                                enabled = uiState.isRootAvailable == true
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            }
                        }
                        // Clear
                        IconButton(onClick = { viewModel.clearLogs() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs")
                        }
                        // Export
                        IconButton(
                            onClick = { viewModel.exportLogs() },
                            enabled = uiState.totalEntries > 0
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Export logs")
                        }
                        // Settings
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Search field
                AnimatedVisibility(
                    visible = showSearch,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = uiState.filterState.searchText,
                        onValueChange = viewModel::setSearchText,
                        placeholder = { Text("Search tag or message...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (uiState.filterState.searchText.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchText("") }) {
                                    Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // Package filter field + save bookmark button
                AnimatedVisibility(
                    visible = showPackageFilter,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    val packageFilter = uiState.filterState.packageFilter
                    val savedFilter = uiState.savedPackageFilter
                    val filterIsSaved = savedFilter.isNotEmpty() && savedFilter == packageFilter

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = packageFilter,
                            onValueChange = viewModel::setPackageFilter,
                            placeholder = { Text("Filter by package (e.g. com.example)") },
                            leadingIcon = { Icon(Icons.Default.Apps, null, Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (packageFilter.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setPackageFilter("") }) {
                                        Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        // Bookmark: save / clear saved filter
                        IconButton(
                            onClick = {
                                if (filterIsSaved) viewModel.clearSavedPackageFilter()
                                else if (packageFilter.isNotEmpty()) viewModel.savePackageFilter()
                            },
                            enabled = packageFilter.isNotEmpty() || savedFilter.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = if (filterIsSaved) Icons.Default.Bookmark
                                              else Icons.Default.BookmarkBorder,
                                contentDescription = if (filterIsSaved) "Clear saved filter"
                                                     else "Save filter for next launch",
                                tint = when {
                                    filterIsSaved -> MaterialTheme.colorScheme.primary
                                    packageFilter.isNotEmpty() -> LocalContentColor.current
                                    else -> LocalContentColor.current.copy(alpha = 0.3f)
                                }
                            )
                        }
                    }
                }

                // Level filter chips
                AnimatedVisibility(
                    visible = showLevelFilter,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            listOf(
                                LogLevel.VERBOSE, LogLevel.DEBUG, LogLevel.INFO,
                                LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL, LogLevel.ASSERT
                            )
                        ) { level ->
                            val selected = level in uiState.filterState.enabledLevels
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleLevel(level) },
                                label = {
                                    Text(
                                        level.char.toString(),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = level.color().copy(alpha = 0.25f),
                                    selectedLabelColor = level.color(),
                                    labelColor = level.color().copy(alpha = 0.5f)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    selectedBorderColor = level.color().copy(alpha = 0.6f),
                                    borderColor = level.color().copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isRootAvailable == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Requesting root access...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                uiState.isRootAvailable == false -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Root Access Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFEF5350),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Simple Logcat needs root to read system logcat.\nGrant root access when prompted.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { viewModel.retryRoot() }) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                filteredEntries.isEmpty() && uiState.totalEntries == 0 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Waiting for log entries...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                filteredEntries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No entries match current filters",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = filteredEntries,
                                key = { _, entry -> entry.id }
                            ) { index, entry ->
                                LogEntryRow(entry = entry, uiState = uiState, isEven = index % 2 == 0)
                            }
                        }
                    }
                    // Scroll-to-bottom FAB (visible when auto-scroll is off)
                    if (!uiState.autoScroll) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        if (filteredEntries.isNotEmpty())
                                            listState.animateScrollToItem(filteredEntries.lastIndex)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Scroll to bottom")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry, uiState: UiState, isEven: Boolean) {
    val levelColor = entry.level.color()
    val rowBg = entry.level.rowBackground()
    val altBg = if (isEven) Color(0x08FFFFFF) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (rowBg != Color.Transparent) rowBg else altBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Level badge
        Box(
            modifier = Modifier
                .padding(top = 1.dp, end = 6.dp)
                .size(17.dp)
                .background(levelColor.copy(alpha = 0.18f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.level.char.toString(),
                color = levelColor,
                fontSize = (uiState.fontSize - 1f).sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Meta row: timestamp + pid/tid + tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.showTimestamp) {
                    Text(
                        text = "${entry.timestamp}  ",
                        color = Color(0xFF616161),
                        fontSize = (uiState.fontSize - 1.5f).sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                }
                if (uiState.showPidTid) {
                    Text(
                        text = "${entry.pid}/${entry.tid}  ",
                        color = Color(0xFF555555),
                        fontSize = (uiState.fontSize - 1.5f).sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                }
                Text(
                    text = entry.tag,
                    color = levelColor,
                    fontSize = uiState.fontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            // Message
            Text(
                text = entry.message,
                color = Color(0xFFDDDDDD),
                fontSize = uiState.fontSize.sp,
                fontFamily = FontFamily.Monospace,
                softWrap = uiState.wrapLines,
                overflow = if (uiState.wrapLines) TextOverflow.Clip else TextOverflow.Ellipsis,
                maxLines = if (uiState.wrapLines) Int.MAX_VALUE else 1
            )
        }
    }
    HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 0.5.dp)
}
