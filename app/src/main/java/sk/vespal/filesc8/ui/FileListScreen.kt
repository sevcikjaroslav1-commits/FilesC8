package sk.vespal.filesc8.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/** Zoznam súborov s filtrom podľa priečinka, triedením a hromadným výberom. */
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
    var chip by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableIntStateOf(0) } // 0 najnovšie, 1 najväčšie, 2 názov
    var sortMenu by remember { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    var askDelete by remember { mutableStateOf(false) }

    val folders: List<Pair<String, Int>> = remember(files) {
        files.groupingBy { it.folder }.eachCount().entries
            .sortedByDescending { it.value }.take(8).map { it.key to it.value }
    }
    val showChips = folders.size > 1
    if (chip > folders.size) chip = 0

    val filtered: List<FItem> = remember(files, chip, sort, folders) {
        val base = if (chip == 0 || !showChips) files else files.filter { it.folder == folders[chip - 1].first }
        when (sort) {
            1 -> base.sortedByDescending { it.size }
            2 -> base.sortedBy { it.name.lowercase() }
            else -> base.sortedByDescending { it.modified }
        }
    }
    // skupiny podľa dňa (len pri triedení podľa dátumu)
    val groups: List<Pair<String, List<FItem>>> = remember(filtered, sort) {
        if (sort != 0) listOf("" to filtered)
        else {
            val out = ArrayList<Pair<String, List<FItem>>>()
            var key = Long.MIN_VALUE
            var cur = ArrayList<FItem>()
            for (f in filtered) {
                val k = dayKey(f.modified)
                if (k != key) {
                    if (cur.isNotEmpty()) out += dayLabel(cur[0].modified) to cur
                    cur = ArrayList()
                    key = k
                }
                cur.add(f)
            }
            if (cur.isNotEmpty()) out += dayLabel(cur[0].modified) to cur
            out
        }
    }

    fun toggle(f: FItem) {
        if (f.path in selection) selection.remove(f.path) else selection.add(f.path)
    }

    fun click(f: FItem) {
        if (selection.isNotEmpty()) toggle(f) else openFile(ctx, f)
    }

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
                if (filtered.isNotEmpty()) {
                    RoundIconButton(Icons.Rounded.SelectAll, tr("Označiť všetko", "Select all")) {
                        if (selection.size == filtered.size) selection.clear()
                        else {
                            selection.clear()
                            selection.addAll(filtered.map { it.path })
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
                ChipsRow(labels, chip, color) { chip = it; selection.clear() }
            }
            if (header != null) header()

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = color)
                }
                filtered.isEmpty() -> EmptyState(Icons.Rounded.FolderOpen, color, tint, emptyText)
                grid -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groups.forEachIndexed { gi, (label, list) ->
                        if (label.isNotEmpty()) {
                            item(key = "h$gi", span = { GridItemSpan(maxLineSpan) }) {
                                DateHeader("$label · ${list.size}")
                            }
                        }
                        items(list, key = { it.path }) { f ->
                            GridCell(f, f.path in selection, selection.isNotEmpty(), { click(f) }, { toggle(f) })
                        }
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEachIndexed { gi, (label, list) ->
                        if (label.isNotEmpty()) item(key = "h$gi") { DateHeader(label) }
                        items(list, key = { it.path }) { f ->
                            FileRow(f, f.path in selection, selection.isNotEmpty(), { click(f) }, { toggle(f) }, showFolder = !showChips || chip == 0)
                        }
                    }
                }
            }
        }

        if (selection.isNotEmpty()) {
            val chosen = filtered.filter { it.path in selection }
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
        val chosen = filtered.filter { it.path in selection }
        DeleteDialog(chosen.size, chosen.sumOf { it.size }, onConfirm = {
            askDelete = false
            scope.launch {
                Repo.delete(ctx, chosen)
                selection.clear()
            }
        }, onDismiss = { askDelete = false })
    }
}
