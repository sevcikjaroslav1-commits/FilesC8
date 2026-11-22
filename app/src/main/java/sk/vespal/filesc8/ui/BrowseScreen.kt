package sk.vespal.filesc8.ui

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.vespal.filesc8.Cat
import sk.vespal.filesc8.FItem
import sk.vespal.filesc8.Repo
import sk.vespal.filesc8.fileToItem
import sk.vespal.filesc8.tr
import java.io.File

private class Entry(val file: File, val isDir: Boolean, val children: Int, val item: FItem)

private fun storageRoots(ctx: Context): List<String> {
    val list = mutableListOf(Environment.getExternalStorageDirectory().absolutePath)
    try {
        ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { d ->
            val p = d.absolutePath.substringBefore("/Android/")
            if (File(p).canRead() && p !in list) list += p
        }
    } catch (_: Exception) {
    }
    return list
}

private fun folderColor(name: String): Color = when (name.lowercase()) {
    "dcim", "camera", "pictures", "screenshots" -> Cat.IMAGES.color
    "movies", "video", "videos" -> Cat.VIDEOS.color
    "music", "podcasts", "ringtones", "notifications", "alarms", "audiobooks", "recordings" -> Cat.AUDIO.color
    "documents" -> Cat.DOCS.color
    "download", "downloads" -> Cat.DOWNLOADS.color
    "android" -> Color(0xFF64748B)
    "whatsapp" -> Color(0xFF16A34A)
    else -> C.Accent
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(onFavorites: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val roots = remember { storageRoots(ctx) }
    var path by rememberSaveable { mutableStateOf(roots[0]) }
    var tick by remember { mutableIntStateOf(0) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    var askDelete by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }

    val root = roots.firstOrNull { path.startsWith(it) } ?: roots[0]

    val entries: List<Entry>? by produceState<List<Entry>?>(null, path, tick, showHidden) {
        value = withContext(Dispatchers.IO) {
            val files = File(path).listFiles() ?: emptyArray()
            files.filter { showHidden || !it.name.startsWith(".") }
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                .map { f ->
                    val dir = f.isDirectory
                    Entry(f, dir, if (dir) (f.list()?.size ?: 0) else 0, fileToItem(f))
                }
        }
    }

    fun go(p: String) {
        selection.clear()
        path = p
    }

    BackHandler(enabled = selection.isNotEmpty()) { selection.clear() }
    BackHandler(enabled = selection.isEmpty() && path != root) { go(File(path).parent ?: root) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("Prehliadať", "Browse"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink, modifier = Modifier.weight(1f))
                    RoundIconButton(
                        if (showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        if (showHidden) tr("Skryť skryté súbory", "Hide hidden files") else tr("Zobraziť skryté súbory", "Show hidden files")
                    ) { showHidden = !showHidden }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    roots.forEachIndexed { i, r ->
                        val st = remember(r) { try { StatFs(r) } catch (e: Exception) { null } }
                        val total = st?.totalBytes ?: 0L
                        val free = st?.availableBytes ?: 0L
                        SourceCard(
                            icon = if (i == 0) Icons.Rounded.PhoneAndroid else Icons.Rounded.SdCard,
                            color = if (i == 0) C.Accent else Color(0xFFB45309),
                            tint = if (i == 0) C.AccentSoft else Color(0xFFFBF0E1),
                            title = if (i == 0) tr("Interné", "Internal") else tr("SD karta", "SD card"),
                            sub = tr("${fmtSize(free)} voľné", "${fmtSize(free)} free"),
                            pct = if (total > 0) (total - free).toFloat() / total else 0f,
                            selected = root == r,
                            modifier = Modifier.weight(1f)
                        ) { go(r) }
                    }
                    SourceCard(
                        icon = Icons.Rounded.Star, color = C.Star, tint = C.StarSoft,
                        title = tr("Obľúbené", "Favorites"), sub = filesWord(Repo.favorites.size),
                        pct = null, selected = false, modifier = Modifier.weight(1f), onClick = onFavorites
                    )
                }
            }
            item {
                // cesta (breadcrumb)
                val rel = path.removePrefix(root).trim('/')
                val segs = if (rel.isEmpty()) emptyList() else rel.split('/')
                val rootLabel = if (root == roots[0]) tr("Interné úložisko", "Internal storage") else tr("SD karta", "SD card")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Crumb(rootLabel, segs.isEmpty()) { go(root) }
                    }
                    itemsIndexed(segs) { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ChevronRight, null, tint = C.Faint, modifier = Modifier.size(16.dp))
                            Crumb(s, i == segs.lastIndex) { go(root + "/" + segs.take(i + 1).joinToString("/")) }
                        }
                    }
                }
            }
            val list = entries
            if (list == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = C.Accent)
                    }
                }
            } else if (list.isEmpty()) {
                item { EmptyState(Icons.Rounded.FolderOpen, C.Accent, C.AccentSoft, tr("Priečinok je prázdny", "This folder is empty")) }
            } else {
                items(list, key = { it.file.absolutePath }) { e ->
                    val p = e.file.absolutePath
                    val sel = p in selection
                    val toggle = { if (sel) selection.remove(p) else selection.add(p); Unit }
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        if (e.isDir) {
                            FolderRow(e, sel, selection.isNotEmpty(), onClick = {
                                if (selection.isNotEmpty()) toggle() else go(p)
                            }, onLongClick = toggle)
                        } else {
                            FileRow(e.item, sel, selection.isNotEmpty(), onClick = {
                                if (selection.isNotEmpty()) toggle() else openFile(ctx, e.item)
                            }, onLongClick = toggle, showFolder = false)
                        }
                    }
                }
            }
        }

        if (selection.isNotEmpty()) {
            val chosen = (entries ?: emptyList()).filter { it.file.absolutePath in selection }
            val chosenItems = chosen.map { it.item }
            Box(Modifier.align(Alignment.BottomCenter)) {
                SelectionBar(
                    count = chosen.size,
                    size = chosen.filter { !it.isDir }.sumOf { it.item.size },
                    allFav = chosen.isNotEmpty() && chosen.all { Repo.isFav(it.file.absolutePath) },
                    onClose = { selection.clear() },
                    onShare = {
                        val files = chosen.filter { !it.isDir }.map { it.item }
                        if (files.isEmpty()) Toast.makeText(ctx, tr("Priečinky sa nedajú zdieľať", "Folders cannot be shared"), Toast.LENGTH_SHORT).show()
                        else shareFiles(ctx, files)
                    },
                    onFav = { Repo.toggleFav(ctx, chosenItems.map { it.path }) },
                    onDelete = { askDelete = true },
                )
            }
        } else {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).height(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(C.Accent).clickable { newFolder = true }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(tr("Priečinok", "Folder"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (askDelete) {
        val chosen = (entries ?: emptyList()).filter { it.file.absolutePath in selection }
        DeleteDialog(chosen.size, chosen.filter { !it.isDir }.sumOf { it.item.size }, onConfirm = {
            askDelete = false
            scope.launch {
                val n = Repo.delete(ctx, chosen.map { it.item })
                if (n < chosen.size) Toast.makeText(ctx, tr("Niektoré položky sa nepodarilo vymazať", "Some items could not be deleted"), Toast.LENGTH_SHORT).show()
                selection.clear()
                tick++
                Repo.refresh(ctx)
            }
        }, onDismiss = { askDelete = false })
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text(tr("Nový priečinok", "New folder"), fontWeight = FontWeight.Bold) },
            text = {
                val shape = RoundedCornerShape(12.dp)
                Box(Modifier.fillMaxWidth().clip(shape).border(1.dp, C.Line, shape).padding(14.dp)) {
                    if (name.isEmpty()) Text(tr("Názov priečinka", "Folder name"), color = C.Muted)
                    BasicTextField(
                        value = name, onValueChange = { name = it.replace("/", "") }, singleLine = true,
                        textStyle = TextStyle(color = C.Ink, fontSize = 16.sp), cursorBrush = SolidColor(C.Accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        val ok = File(path, n).mkdirs()
                        if (!ok) Toast.makeText(ctx, tr("Priečinok sa nepodarilo vytvoriť", "Could not create the folder"), Toast.LENGTH_SHORT).show()
                        tick++
                    }
                    newFolder = false
                }) { Text(tr("Vytvoriť", "Create"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(tr("Zrušiť", "Cancel")) } },
            containerColor = C.Surface,
        )
    }
}

@Composable
private fun Crumb(text: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp,
        color = if (current) C.Ink else C.Accent,
        fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp)
    )
}

@Composable
private fun SourceCard(
    icon: ImageVector,
    color: Color,
    tint: Color,
    title: String,
    sub: String,
    pct: Float?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(C.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) color else C.Line, shape)
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        SoftTile(icon, color, tint, size = 36.dp, radius = 12.dp, iconSize = 20.dp)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Ink, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        if (pct != null) {
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEEF1F6))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pct.coerceIn(0f, 1f)).background(color))
            }
        }
        Text(sub, fontSize = 11.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(e: Entry, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val color = folderColor(e.file.name)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) C.AccentSoft else C.Surface)
            .border(1.dp, if (selected) C.Accent else C.Line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftTile(Icons.Rounded.Folder, color, color.copy(alpha = 0.12f), size = 48.dp, radius = 13.dp, iconSize = 26.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.file.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${itemsWord(e.children)} · ${fmtDate(e.file.lastModified())}",
                fontSize = 12.sp, color = C.Muted, maxLines = 1, modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) C.Accent else C.Faint, modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = C.Faint, modifier = Modifier.size(20.dp))
        }
    }
}
