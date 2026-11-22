package sk.vespal.filesc8.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.vespal.filesc8.FItem
import sk.vespal.filesc8.Repo
import sk.vespal.filesc8.tr

private fun sortFiles(list: List<FItem>, sort: Int): List<FItem> = when (sort) {
    1 -> list.sortedByDescending { it.size }
    2 -> list.sortedBy { it.name.lowercase() }
    else -> list.sortedByDescending { it.modified }
}

/** Skupiny podľa dňa (len pri triedení podľa dátumu). */
private fun groupByDay(list: List<FItem>, sort: Int): List<Pair<String, List<FItem>>> {
    if (sort != 0) return listOf("" to list)
    val out = ArrayList<Pair<String, List<FItem>>>()
    var key = Long.MIN_VALUE
    var cur = ArrayList<FItem>()
    for (f in list) {
        val k = dayKey(f.modified)
        if (k != key) {
            if (cur.isNotEmpty()) out += dayLabel(cur[0].modified) to cur
            cur = ArrayList()
            key = k
        }
        cur.add(f)
    }
    if (cur.isNotEmpty()) out += dayLabel(cur[0].modified) to cur
    return out
}

/**
 * Zoznam súborov s podpriečinkami (prepínajú sa tlačidlami hore alebo potiahnutím
 * doľava/doprava), triedením a hromadným výberom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListScreen(
    title: String,
    icon: ImageVector?,
    color: Color,
    tint: Color,
    files: List<FItem>,
    grid: Boolean,
    onBack: () -> Unit,
    emptyText: String,
    loading: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sort by rememberSaveable { mutableIntStateOf(0) } // 0 najnovšie, 1 najväčšie, 2 názov
    var sortMenu by remember { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    var askDelete by remember { mutableStateOf(false) }

    val folders: List<Pair<String, Int>> = remember(files) {
        files.groupingBy { it.folder }.eachCount().entries
            .sortedByDescending { it.value }.take(8).map { it.key to it.value }
    }
    val showChips = folders.size > 1
    val pageCount = if (showChips) folders.size + 1 else 1
    val pager = rememberPagerState(pageCount = { pageCount })
    val page = pager.currentPage.coerceIn(0, pageCount - 1)

    fun pageFiles(p: Int): List<FItem> =
        if (p == 0 || !showChips || p > folders.size) files
        else files.filter { it.folder == folders[p - 1].first }

    // súbory aktuálnej strany – pre "Označiť všetko" a panel výberu
    val current: List<FItem> = remember(files, page, sort, folders) { sortFiles(pageFiles(page), sort) }

    // pri prepnutí podpriečinka zrušiť výber
    LaunchedEffect(page) { selection.clear() }

    BackHandler(enabled = selection.isNotEmpty()) { selection.clear() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                title = title,
                subtitle = "${filesWord(files.size)} · ${fmtSize(files.sumOf { it.size })}",
                icon = icon,
                color = color,
                onBack = onBack,
            ) {
                if (current.isNotEmpty()) {
                    RoundIconButton(Icons.Rounded.SelectAll, tr("Označiť všetko", "Select all")) {
                        if (selection.size == current.size) selection.clear()
                        else {
                            selection.clear()
                            selection.addAll(current.map { it.path })
                        }
                    }
                }
                Box {
                    RoundIconButton(Icons.Rounded.SwapVert, tr("Triediť", "Sort")) { sortMenu = true }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        val opts = listOf(tr("Najnovšie", "Newest"), tr("Najväčšie", "Largest"), tr("Názov A–Z", "Name A–Z"))
                        opts.forEachIndexed { i, o ->
                            DropdownMenuItem(
                                text = { Text(o, fontWeight = if (i == sort) FontWeight.Bold else FontWeight.Normal, color = if (i == sort) C.Accent else C.Ink) },
                                onClick = { sort = i; sortMenu = false }
                            )
                        }
                    }
                }
            }
            if (showChips) {
                val labels = listOf(tr("Všetko", "All") + " " + files.size) + folders.map { "${it.first} ${it.second}" }
                ChipsRow(labels, page, color) { i -> scope.launch { pager.animateScrollToPage(i) } }
            }
            if (header != null) header()

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = color)
                }
                files.isEmpty() -> EmptyState(Icons.Rounded.FolderOpen, color, tint, emptyText)
                else -> HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { p -> if (p == 0 || p > folders.size) "\u0000all" else folders[p - 1].first },
                ) { p ->
                    val list = remember(files, p, sort, folders) { sortFiles(pageFiles(p), sort) }
                    FilePage(
                        list = list,
                        grid = grid,
                        sort = sort,
                        showFolder = p == 0,
                        selection = selection,
                        onOpen = { openFile(ctx, it, list) },
                        emptyText = emptyText,
                        color = color,
                        tint = tint,
                    )
                }
            }
        }

        if (selection.isNotEmpty()) {
            val chosen = current.filter { it.path in selection }
            Box(Modifier.align(Alignment.BottomCenter)) {
                SelectionBar(
                    count = chosen.size,
                    size = chosen.sumOf { it.size },
                    allFav = chosen.isNotEmpty() && chosen.all { Repo.isFav(it.path) },
                    onClose = { selection.clear() },
                    onShare = { shareFiles(ctx, chosen) },
                    onFav = { Repo.toggleFav(ctx, chosen.map { it.path }) },
                    onDelete = { askDelete = true },
                )
            }
        }
    }

    if (askDelete) {
        val chosen = current.filter { it.path in selection }
        DeleteDialog(chosen.size, chosen.sumOf { it.size }, onConfirm = {
            askDelete = false
            scope.launch {
                Repo.delete(ctx, chosen)
                selection.clear()
            }
        }, onDismiss = { askDelete = false })
    }
}

@Composable
private fun FilePage(
    list: List<FItem>,
    grid: Boolean,
    sort: Int,
    showFolder: Boolean,
    selection: SnapshotStateList<String>,
    onOpen: (FItem) -> Unit,
    emptyText: String,
    color: Color,
    tint: Color,
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize()) { EmptyState(Icons.Rounded.FolderOpen, color, tint, emptyText) }
        return
    }
    val groups = remember(list, sort) { groupByDay(list, sort) }

    fun toggle(f: FItem) {
        if (f.path in selection) selection.remove(f.path) else selection.add(f.path)
    }

    fun click(f: FItem) {
        if (selection.isNotEmpty()) toggle(f) else onOpen(f)
    }

    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            groups.forEachIndexed { gi, (label, group) ->
                if (label.isNotEmpty()) {
                    item(key = "h$gi", span = { GridItemSpan(maxLineSpan) }) {
                        DateHeader("$label · ${group.size}")
                    }
                }
                items(group, key = { it.path }) { f ->
                    GridCell(f, f.path in selection, selection.isNotEmpty(), { click(f) }, { toggle(f) })
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEachIndexed { gi, (label, group) ->
                if (label.isNotEmpty()) item(key = "h$gi") { DateHeader(label) }
                items(group, key = { it.path }) { f ->
                    FileRow(f, f.path in selection, selection.isNotEmpty(), { click(f) }, { toggle(f) }, showFolder = showFolder)
                }
            }
        }
    }
}
