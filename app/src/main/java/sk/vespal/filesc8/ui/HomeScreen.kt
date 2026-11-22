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
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Správca súborov", "File manager"), fontSize = 13.sp, color = C.Muted, fontWeight = FontWeight.Medium)
                        Text(tr("Súbory", "Files"), fontSize = 28.sp, color = C.Ink, fontWeight = FontWeight.ExtraBold)
                    }
                    LangSwitch()
                }
            }
            item {
                SearchField(query, { query = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
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
                item { StorageCard(onClean, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
                item {
                    SectionTitle(tr("Kategórie", "Categories"), Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp))
                }
                items(Cat.entries.chunked(2)) { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (c in pair) CategoryTile(c, Modifier.weight(1f)) { onCategory(c) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                val recent = snap.files.take(20)
                if (recent.isNotEmpty()) {
                    item {
                        SectionTitle(tr("Nedávne", "Recent"), Modifier.padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 6.dp))
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recent, key = { it.path }) { f -> RecentCard(f) { openFile(ctx, f) } }
                        }
                    }
                }
                item {
                    SectionTitle(tr("Zbierky", "Collections"), Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp))
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CollectionCard(
                            Icons.Rounded.Star, C.Star, C.StarSoft, tr("Obľúbené", "Favorites"),
                            filesWord(Repo.favorites.size), Modifier.weight(1f), onFavorites
                        )
                        CollectionCard(
                            Icons.Rounded.BarChart, Color(0xFF7C3AED), Color(0xFFF0EAFE), tr("Veľké", "Large"),
                            filesWord(Repo.sugItems(Sug.BIG, snap).size), Modifier.weight(1f), onBig
                        )
                        CollectionCard(
                            Icons.Rounded.PhoneAndroid, C.Accent, C.AccentSoft, tr("Úložisko", "Storage"),
                            tr("Priečinky", "Folders"), Modifier.weight(1f), onBrowse
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
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(shape).background(C.Surface).border(1.dp, C.Line, shape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = C.Muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (value.isEmpty()) {
                Text(tr("Hľadať súbory, napr. „faktúra“", "Search files, e.g. “invoice”"), color = C.Muted, fontSize = 15.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = C.Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            RoundIconButton(Icons.Rounded.Close, tr("Vymazať hľadanie", "Clear search"), tint = C.Muted) { onChange("") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageCard(onClean: () -> Unit, modifier: Modifier = Modifier) {
    val snap = Repo.snap
    val total = snap.total
    val used = snap.used
    val pct = if (total > 0) used.toFloat() / total else 0f
    Panel(modifier.fillMaxWidth(), radius = 22.dp) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 10.dp.toPx()
                        val inset = sw / 2
                        val arcSize = Size(size.width - sw, size.height - sw)
                        drawArc(C.AccentSoft, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                        drawArc(C.Accent, -90f, 360f * pct, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(pct * 100).toInt()} %", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink)
                        Text(tr("využité", "used"), fontSize = 10.sp, color = C.Muted)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = C.Muted, modifier = Modifier.size(16.dp))
                        Text(tr("Interné úložisko", "Internal storage"), fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(start = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        Text(fmtSize(used), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink)
                        Text(
                            tr(" z ${fmtSize(total)}", " of ${fmtSize(total)}"), fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = C.Muted, modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Text(
                        tr("Voľné: ${fmtSize(snap.free)}", "Free: ${fmtSize(snap.free)}"),
                        fontSize = 13.sp, color = C.Green, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // farebný pruh podľa kategórií
            val parts = listOf(Cat.APPS, Cat.VIDEOS, Cat.IMAGES, Cat.AUDIO, Cat.DOCS, Cat.ARCHIVES)
                .map { it to snap.catSize(it) }
            val known = parts.sumOf { it.second }
            val other = (used - known).coerceAtLeast(0)
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEEF1F6))
            ) {
                if (total > 0) {
                    for ((c, s) in parts) {
                        val w = s.toFloat() / total
                        if (w > 0.002f) Box(Modifier.fillMaxHeight().weight(w).background(c.color))
                    }
                    val wo = other.toFloat() / total
                    if (wo > 0.002f) Box(Modifier.fillMaxHeight().weight(wo).background(C.Faint))
                    val wf = snap.free.toFloat() / total
                    if (wf > 0.002f) Spacer(Modifier.fillMaxHeight().weight(wf))
                }
            }
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for ((c, s) in parts) if (s > 0) Legend(c.color, "${c.label} ${fmtSize(s)}")
                if (other > 0) Legend(C.Faint, tr("Ostatné ${fmtSize(other)}", "Other ${fmtSize(other)}"))
            }

            val freeable = Repo.freeable(snap)
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(C.Accent).clickable(onClick = onClean),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    if (freeable > 0) tr("Uvoľniť ${fmtSize(freeable)}", "Free up ${fmtSize(freeable)}") else tr("Vyčistiť", "Clean up"),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun CategoryTile(c: Cat, modifier: Modifier, onClick: () -> Unit) {
    val snap = Repo.snap
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(C.Surface).border(1.dp, C.Line, shape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        IconTile(c.icon, c.color, size = 42.dp, radius = 13.dp, iconSize = 21.dp)
        Text(
            c.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)
        )
        Row(Modifier.padding(top = 2.dp)) {
            Text(fmtCount(snap.catCount(c)) + " · ", fontSize = 12.sp, color = C.Muted)
            Text(fmtSize(snap.catSize(c)), fontSize = 12.sp, color = c.color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecentCard(f: FItem, onClick: () -> Unit) {
    Column(Modifier.width(128.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        FileThumb(f, Modifier.width(128.dp).height(144.dp).clip(RoundedCornerShape(16.dp)), iconSize = 34.dp)
        Text(
            f.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
        )
        Text(fmtSize(f.size), fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
    }
}

@Composable
private fun CollectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    tint: Color,
    title: String,
    sub: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(C.Surface).border(1.dp, C.Line, shape).clickable(onClick = onClick).padding(12.dp)
    ) {
        SoftTile(icon, color, tint, size = 36.dp, radius = 12.dp, iconSize = 20.dp)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
        Text(sub, fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
