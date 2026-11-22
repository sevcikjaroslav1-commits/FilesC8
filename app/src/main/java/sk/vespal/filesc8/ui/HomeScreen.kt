package sk.vespal.filesc8.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import sk.vespal.filesc8.Cat
import sk.vespal.filesc8.FItem
import sk.vespal.filesc8.Repo
import sk.vespal.filesc8.Sug
import sk.vespal.filesc8.tr
import sk.vespal.filesc8.typeCat

@Composable
fun HomeScreen(
    onCategory: (Cat) -> Unit,
    onClean: () -> Unit,
    onFavorites: () -> Unit,
    onBig: () -> Unit,
    onBrowse: () -> Unit,
) {
    val ctx = LocalContext.current
    val snap = Repo.snap
    var query by rememberSaveable { mutableStateOf("") }
    val selection = remember { mutableStateListOf<String>() }
    var askDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val results: List<FItem> = remember(snap, query) {
        val q = query.trim()
        if (q.length < 2) emptyList()
        else snap.files.filter { it.name.contains(q, ignoreCase = true) }.take(300)
    }
    val searching = query.trim().isNotEmpty()

    BackHandler(enabled = selection.isNotEmpty()) { selection.clear() }
    BackHandler(enabled = selection.isEmpty() && searching) { query = "" }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 14.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("Súbory", "Files"), fontSize = 24.sp, color = C.Ink, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    LangSwitch()
                }
            }
            item {
                SearchField(query, { query = it }, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp))
            }
            if (Repo.loading && !snap.loaded) {
                item {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clip(RoundedCornerShape(4.dp)),
                        color = C.Accent, trackColor = C.AccentSoft
                    )
                }
            }

            if (searching) {
                item {
                    Text(
                        if (query.trim().length < 2) tr("Napíš aspoň 2 znaky", "Type at least 2 characters")
                        else tr("Nájdené: ${results.size}", "Found: ${results.size}"),
                        color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
                    )
                }
                if (query.trim().length >= 2 && results.isEmpty()) {
                    item { EmptyState(Icons.Rounded.Search, C.Accent, C.AccentSoft, tr("Nič sa nenašlo", "Nothing found")) }
                }
                items(results, key = { it.path }) { f ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        val sel = f.path in selection
                        FileRow(
                            f, sel, selection.isNotEmpty(),
                            onClick = {
                                if (selection.isNotEmpty()) { if (sel) selection.remove(f.path) else selection.add(f.path) }
                                else openFile(ctx, f)
                            },
                            onLongClick = { if (sel) selection.remove(f.path) else selection.add(f.path) }
                        )
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            } else {
                item { StorageCard(onClean, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) }
                item {
                    Text(
                        tr("Kategórie", "Categories"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
                    )
                }
                items(Cat.entries.chunked(4)) { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in row) CategoryTile(c, Modifier.weight(1f)) { onCategory(c) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                val recent = snap.files.filter { it.isImage || it.isVideo }.take(20)
                if (recent.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tr("Nedávne", "Recent"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink, modifier = Modifier.weight(1f))
                            Text(
                                tr("Všetko", "All"), color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCategory(Cat.IMAGES) }.padding(8.dp)
                            )
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recent, key = { it.path }) { f -> RecentCard(f) { openFile(ctx, f) } }
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CollectionChip(
                            Icons.Rounded.Star, C.Star, C.StarSoft, tr("Obľúbené", "Favorites"),
                            Repo.favorites.size.toString(), Modifier.weight(1f), onFavorites
                        )
                        CollectionChip(
                            Icons.Rounded.BarChart, Color(0xFF7C3AED), Color(0xFFF0EAFE), tr("Veľké", "Large"),
                            Repo.sugItems(Sug.BIG, snap).size.toString(), Modifier.weight(1f), onBig
                        )
                        CollectionChip(
                            Icons.Rounded.PhoneAndroid, C.Accent, C.AccentSoft, tr("Priečinky", "Folders"),
                            "", Modifier.weight(1f), onBrowse
                        )
                    }
                }
            }
        }

        if (selection.isNotEmpty()) {
            val chosen = results.filter { it.path in selection }
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
        val chosen = results.filter { it.path in selection }
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
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(shape).background(C.Surface).border(1.dp, C.Line, shape)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = C.Muted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (value.isEmpty()) {
                Text(tr("Hľadať súbory", "Search files"), color = C.Muted, fontSize = 14.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = C.Ink, fontSize = 14.sp),
                cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { onChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, tr("Vymazať hľadanie", "Clear search"), tint = C.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StorageCard(onClean: () -> Unit, modifier: Modifier = Modifier) {
    val snap = Repo.snap
    val total = snap.total
    val used = snap.used
    val pct = if (total > 0) used.toFloat() / total else 0f
    Panel(modifier.fillMaxWidth(), radius = 18.dp) {
        Column(Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 7.dp.toPx()
                        val inset = sw / 2
                        val arcSize = Size(size.width - sw, size.height - sw)
                        drawArc(C.AccentSoft, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                        drawArc(C.Accent, -90f, 360f * pct, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    Text("${(pct * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmtSize(used), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink)
                        Text(
                            tr(" z ${fmtSize(total)}", " of ${fmtSize(total)}"), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = C.Muted, modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                    Text(
                        tr("Voľné ${fmtSize(snap.free)}", "Free ${fmtSize(snap.free)}"),
                        fontSize = 12.sp, color = C.Green, fontWeight = FontWeight.SemiBold
                    )
                }
                val freeable = Repo.freeable(snap)
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(C.Accent)
                        .clickable(onClick = onClean).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        if (freeable > 0) tr("Uvoľniť ${fmtSize(freeable)}", "Free ${fmtSize(freeable)}") else tr("Vyčistiť", "Clean"),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            // farebný pruh podľa kategórií
            val parts = listOf(Cat.APPS, Cat.VIDEOS, Cat.IMAGES, Cat.AUDIO, Cat.DOCS, Cat.ARCHIVES)
                .map { it to snap.catSize(it) }
            val known = parts.sumOf { it.second }
            val other = (used - known).coerceAtLeast(0)
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEF1F6))
            ) {
                if (total > 0) {
                    for ((c, sz) in parts) {
                        val w = sz.toFloat() / total
                        if (w > 0.002f) Box(Modifier.fillMaxHeight().weight(w).background(c.color))
                    }
                    val wo = other.toFloat() / total
                    if (wo > 0.002f) Box(Modifier.fillMaxHeight().weight(wo).background(C.Faint))
                    val wf = snap.free.toFloat() / total
                    if (wf > 0.002f) Spacer(Modifier.fillMaxHeight().weight(wf))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(c: Cat, modifier: Modifier, onClick: () -> Unit) {
    val snap = Repo.snap
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(C.Surface).border(1.dp, C.Line, shape).clickable(onClick = onClick)
            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconTile(c.icon, c.color, size = 38.dp, radius = 12.dp, iconSize = 19.dp)
        Text(
            shortLabel(c), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
        )
        Text(fmtSize(snap.catSize(c)), fontSize = 11.sp, color = c.color, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Kratšie názvy pre úzke dlaždice. */
private fun shortLabel(c: Cat): String = when (c) {
    Cat.ARCHIVES -> tr("Archívy", "Archives")
    Cat.SCREENSHOTS -> tr("Snímky", "Screens")
    else -> c.label
}

private fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
private fun RecentCard(f: FItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.width(112.dp).height(136.dp).clip(shape).clickable(onClick = onClick)) {
        FileThumb(f, Modifier.fillMaxSize(), iconSize = 32.dp)
        val t = f.typeCat()
        if (t != null) {
            Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                IconTile(t.icon, t.color, size = 24.dp, radius = 8.dp, iconSize = 13.dp)
            }
        }
        if (f.isVideo && f.duration > 0) {
            Badge(fmtDuration(f.duration), Modifier.align(Alignment.BottomStart))
        }
        Badge(fmtSize(f.size), Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    Text(
        text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x8C000000))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun CollectionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    tint: Color,
    title: String,
    count: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(44.dp).clip(shape).background(C.Surface).border(1.dp, C.Line, shape).clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftTile(icon, color, tint, size = 28.dp, radius = 9.dp, iconSize = 16.dp)
        Text(
            title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        if (count.isNotEmpty()) Text(count, fontSize = 12.sp, color = C.Muted)
    }
}
