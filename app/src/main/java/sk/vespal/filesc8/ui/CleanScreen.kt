package sk.vespal.filesc8.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.vespal.filesc8.Repo
import sk.vespal.filesc8.Sug
import sk.vespal.filesc8.tr

fun sugIcon(s: Sug): ImageVector = when (s) {
    Sug.BIG -> Icons.Rounded.BarChart
    Sug.DUPES -> Icons.Rounded.ContentCopy
    Sug.OLD_SHOTS -> Icons.Rounded.Screenshot
    Sug.APKS -> Icons.Rounded.Archive
    Sug.OLD_DOWNLOADS -> Icons.Rounded.FileDownload
}

@Composable
fun CleanScreen(onSug: (Sug) -> Unit) {
    val snap = Repo.snap
    LaunchedEffect(snap) { if (snap.loaded) Repo.computeDupes() }
    val total = Repo.freeable(snap)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                tr("Vyčistiť", "Clean"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 12.dp)
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)
                    .clip(RoundedCornerShape(24.dp)).background(C.Accent).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Môžete uvoľniť", "You can free up"), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    Text(fmtSize(total), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        tr("Po vyčistení: ${fmtSize(snap.free + total)} voľné", "After cleaning: ${fmtSize(snap.free + total)} free"),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
                    )
                }
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
        item {
            Text(
                tr("Návrhy", "Suggestions"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink,
                modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp)
            )
        }
        items(Sug.entries) { s ->
            val list = Repo.sugItems(s, snap)
            val running = s == Sug.DUPES && (Repo.dupesRunning || Repo.dupes == null)
            val shape = RoundedCornerShape(20.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                    .clip(shape).background(C.Surface).border(1.dp, C.Line, shape)
                    .clickable(enabled = !running) { onSug(s) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoftTile(sugIcon(s), s.color, s.tint, size = 46.dp, radius = 14.dp, iconSize = 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    Text(s.desc, fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(top = 2.dp))
                    if (running) {
                        Text(tr("Hľadám…", "Searching…"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = s.color, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text(
                            if (list.isEmpty()) tr("Nič na vyčistenie", "Nothing to clean")
                            else "${fmtSize(list.sumOf { it.size })} · ${filesWord(list.size)}",
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (list.isEmpty()) C.Green else s.color, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (running) CircularProgressIndicator(Modifier.size(22.dp), color = s.color, strokeWidth = 2.5.dp)
                else Icon(Icons.Rounded.ChevronRight, null, tint = C.Faint)
            }
        }
    }
}
