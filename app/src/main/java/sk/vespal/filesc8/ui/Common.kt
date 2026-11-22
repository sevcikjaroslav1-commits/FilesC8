package sk.vespal.filesc8.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.vespal.filesc8.FItem
import sk.vespal.filesc8.Lang
import sk.vespal.filesc8.Repo
import sk.vespal.filesc8.mimeOf
import sk.vespal.filesc8.tr
import sk.vespal.filesc8.typeCat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- formátovanie

private fun loc(): Locale = if (Lang.code == "en") Locale.US else Locale.forLanguageTag("sk")

fun fmtSize(b: Long): String {
    val kb = 1024.0
    return when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format(loc(), "%.0f kB", b / kb)
        b < 1024L * 1024 * 1024 -> String.format(loc(), "%.1f MB", b / (kb * kb))
        else -> String.format(loc(), "%.1f GB", b / (kb * kb * kb))
    }
}

fun fmtCount(n: Int): String = String.format(loc(), "%,d", n)

private fun dayStart(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun fmtDate(ms: Long): String {
    val pattern = if (Lang.code == "en") "d MMM yyyy" else "d. M. yyyy"
    return SimpleDateFormat(pattern, loc()).format(Date(ms))
}

/** "Dnes", "Včera", alebo dátum – nadpis skupiny. */
fun dayLabel(ms: Long): String {
    val today = dayStart(System.currentTimeMillis())
    val d = dayStart(ms)
    return when {
        d >= today -> tr("Dnes", "Today")
        d >= today - 24L * 3600 * 1000 -> tr("Včera", "Yesterday")
        else -> fmtDate(ms)
    }
}

fun dayKey(ms: Long): Long = dayStart(ms)

fun itemsWord(n: Int): String = if (Lang.code == "en") {
    if (n == 1) "1 item" else "${fmtCount(n)} items"
} else when (n) {
    1 -> "1 položka"
    2, 3, 4 -> "$n položky"
    else -> "${fmtCount(n)} položiek"
}

fun filesWord(n: Int): String = if (Lang.code == "en") {
    if (n == 1) "1 file" else "${fmtCount(n)} files"
} else when (n) {
    1 -> "1 súbor"
    2, 3, 4 -> "$n súbory"
    else -> "${fmtCount(n)} súborov"
}

// ---------------------------------------------------------------- akcie so súbormi

private fun uriFor(ctx: Context, path: String): Uri =
    FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(path))

fun openFile(ctx: Context, item: FItem) {
    try {
        val mime = if (item.mime == "*/*") mimeOf(item.name) else item.mime
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(ctx, item.path), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, tr("Žiadna aplikácia nevie otvoriť tento súbor", "No app can open this file"), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, tr("Súbor sa nedá otvoriť", "Cannot open the file"), Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(ctx: Context, items: List<FItem>) {
    if (items.isEmpty()) return
    try {
        val uris = ArrayList(items.map { uriFor(ctx, it.path) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items[0].mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        val clip = ClipData.newRawUri("", uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, tr("Zdieľať", "Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, tr("Zdieľanie zlyhalo", "Sharing failed"), Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------- náhľady

object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val misses = HashSet<String>()

    fun peek(path: String): Bitmap? = cache.get(path)

    fun load(item: FItem): Bitmap? {
        cache.get(item.path)?.let { return it }
        synchronized(misses) { if (item.path in misses) return null }
        val f = File(item.path)
        val sz = Size(320, 320)
        val b: Bitmap? = try {
            when {
                item.isImage -> ThumbnailUtils.createImageThumbnail(f, sz, null)
                item.isVideo -> ThumbnailUtils.createVideoThumbnail(f, sz, null)
                item.isAudio -> ThumbnailUtils.createAudioThumbnail(f, sz, null)
                else -> null
            }
        } catch (e: Throwable) {
            null
        }
        if (b != null) cache.put(item.path, b) else synchronized(misses) { misses.add(item.path) }
        return b
    }
}

@Composable
fun FileThumb(item: FItem, modifier: Modifier = Modifier, iconSize: Dp = 24.dp, showPlay: Boolean = true) {
    val t = item.typeCat()
    val canThumb = item.isImage || item.isVideo || item.isAudio
    val bmp: ImageBitmap? by produceState<ImageBitmap?>(Thumbs.peek(item.path)?.asImageBitmap(), item.path) {
        if (value == null && canThumb) {
            value = withContext(Dispatchers.IO) { Thumbs.load(item) }?.asImageBitmap()
        }
    }
    Box(modifier.background(t?.tint ?: C.Bg), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                t?.icon ?: Icons.Rounded.InsertDriveFile, contentDescription = null,
                tint = t?.color ?: C.Muted, modifier = Modifier.size(iconSize)
            )
        }
        if (item.isVideo && showPlay && b != null) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- stavebné bloky

@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 44.dp, radius: Dp = 14.dp, iconSize: Dp = 22.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun SoftTile(icon: ImageVector, color: Color, tint: Color, size: Dp = 40.dp, radius: Dp = 12.dp, iconSize: Dp = 22.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, radius: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.clip(shape).background(C.Surface).border(1.dp, C.Line, shape),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, color = C.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp)
            )
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, label: String, tint: Color = C.Ink, bg: Color = Color.Transparent, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun TopBar(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    color: Color,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Späť", "Back"), onClick = onBack)
        if (icon != null) {
            Spacer(Modifier.width(4.dp))
            IconTile(icon, color, size = 36.dp, radius = 11.dp, iconSize = 19.dp)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = C.Muted, maxLines = 1)
        }
        actions()
    }
}

@Composable
fun ChipsRow(labels: List<String>, selected: Int, color: Color, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    // vybrané tlačidlo vždy viditeľné (aj pri prepínaní potiahnutím)
    LaunchedEffect(selected) {
        if (selected in labels.indices) listState.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(labels) { i, l ->
            val on = i == selected
            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier.height(36.dp).clip(shape)
                    .background(if (on) color else C.Surface)
                    .border(1.dp, if (on) color else C.Line, shape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(l, color = if (on) Color.White else C.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun DateHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Muted, letterSpacing = 0.6.sp,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showFolder: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) C.AccentSoft else C.Surface)
            .border(1.dp, if (selected) C.Accent else C.Line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumb(item, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), iconSize = 24.dp, showPlay = false)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                append(fmtSize(item.size)); append(" · "); append(fmtDate(item.modified))
                if (showFolder) { append(" · "); append(item.folder) }
            }
            Text(meta, fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) C.Accent else C.Faint, modifier = Modifier.size(24.dp)
            )
        } else if (Repo.isFav(item.path)) {
            Icon(Icons.Rounded.Star, tr("Obľúbené", "Favorite"), tint = C.Star, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridCell(item: FItem, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier.aspectRatio(0.82f).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        FileThumb(item, Modifier.fillMaxSize(), iconSize = 30.dp)
        Text(
            fmtSize(item.size), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color(0x8C000000)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
        if (Repo.isFav(item.path) && !selecting) {
            Icon(
                Icons.Rounded.Star, null, tint = C.Star,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(18.dp)
            )
        }
        if (selecting) {
            if (selected) Box(Modifier.fillMaxSize().background(C.Accent.copy(alpha = 0.28f)))
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
            )
        }
    }
}

@Composable
fun SelectionBar(
    count: Int,
    size: Long,
    allFav: Boolean,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onFav: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
            .clip(RoundedCornerShape(20.dp)).background(C.Ink).padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.Rounded.Close, tr("Zrušiť výber", "Clear selection"), tint = Color.White, onClick = onClose)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(tr("$count vybrané", "$count selected"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(fmtSize(size), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        RoundIconButton(Icons.Rounded.Share, tr("Zdieľať", "Share"), tint = Color.White, bg = Color(0x1FFFFFFF), onClick = onShare)
        Spacer(Modifier.width(6.dp))
        RoundIconButton(
            if (allFav) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr("Obľúbené", "Favorite"),
            tint = if (allFav) C.Star else Color.White, bg = Color(0x1FFFFFFF), onClick = onFav
        )
        Spacer(Modifier.width(6.dp))
        RoundIconButton(Icons.Rounded.Delete, tr("Vymazať", "Delete"), tint = Color.White, bg = C.Red, onClick = onDelete)
    }
}

@Composable
fun DeleteDialog(count: Int, size: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Vymazať natrvalo?", "Delete permanently?"), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                tr(
                    "${filesWord(count)} (${fmtSize(size)}) sa vymaže z telefónu. Túto akciu nejde vrátiť.",
                    "${filesWord(count)} (${fmtSize(size)}) will be deleted from the phone. This cannot be undone."
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(tr("Vymazať", "Delete"), color = C.Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Zrušiť", "Cancel")) }
        },
        containerColor = C.Surface,
    )
}

@Composable
fun EmptyState(icon: ImageVector, color: Color, tint: Color, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SoftTile(icon, color, tint, size = 72.dp, radius = 24.dp, iconSize = 36.dp)
        Text(text, color = C.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

// ---------------------------------------------------------------- spodná navigácia

@Composable
fun BottomNav(tab: Int, onTab: (Int) -> Unit) {
    val items = listOf(
        Triple(Icons.Rounded.Home, tr("Prehľad", "Overview"), 0),
        Triple(Icons.Rounded.Folder, tr("Prehliadať", "Browse"), 1),
        Triple(Icons.Rounded.DeleteSweep, tr("Vyčistiť", "Clean"), 2),
    )
    Column(Modifier.fillMaxWidth().background(C.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for ((icon, label, i) in items) {
                val on = i == tab
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Tab) { onTab(i) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.width(60.dp).height(32.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) C.AccentSoft else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = if (on) C.Accent else C.Muted, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        label, fontSize = 12.sp, color = if (on) C.Accent else C.Muted,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- prepínač jazyka SK / EN

@Composable
fun LangSwitch() {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.clip(shape).background(C.Surface).border(1.dp, C.Line, shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        LangButton("SK", Lang.code == "sk", { slovakFlag() }) { Lang.set(ctx, "sk") }
        LangButton("EN", Lang.code == "en", { ukFlag() }) { Lang.set(ctx, "en") }
    }
}

@Composable
private fun LangButton(code: String, selected: Boolean, flag: DrawScope.() -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) C.AccentSoft else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 24.dp, height = 16.dp).clip(RoundedCornerShape(3.dp))
                .border(0.5.dp, Color(0x330F172A), RoundedCornerShape(3.dp))
        ) {
            Canvas(Modifier.size(width = 24.dp, height = 16.dp)) { flag() }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            code, color = if (selected) C.Accent else C.Muted, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
        )
    }
}

/** Slovenská vlajka: biela, modrá, červená + zjednodušený štít s dvojkrížom. */
private fun DrawScope.slovakFlag() {
    val w = size.width
    val h = size.height
    drawRect(Color.White, Offset.Zero, GSize(w, h / 3f))
    drawRect(Color(0xFF0B4EA2), Offset(0f, h / 3f), GSize(w, h / 3f))
    drawRect(Color(0xFFEE1C25), Offset(0f, 2 * h / 3f), GSize(w, h / 3f))
    val sx = w * 0.2f
    val sw = w * 0.28f
    val sy = h * 0.18f
    val sh = h * 0.64f
    val shield = Path().apply {
        moveTo(sx, sy)
        lineTo(sx + sw, sy)
        lineTo(sx + sw, sy + sh * 0.6f)
        quadraticTo(sx + sw, sy + sh * 0.9f, sx + sw / 2f, sy + sh)
        quadraticTo(sx, sy + sh * 0.9f, sx, sy + sh * 0.6f)
        close()
    }
    drawPath(shield, Color.White)
    val inset = w * 0.015f
    val inner = Path().apply {
        moveTo(sx + inset, sy + inset)
        lineTo(sx + sw - inset, sy + inset)
        lineTo(sx + sw - inset, sy + sh * 0.6f)
        quadraticTo(sx + sw - inset, sy + sh * 0.88f, sx + sw / 2f, sy + sh - inset * 1.5f)
        quadraticTo(sx + inset, sy + sh * 0.88f, sx + inset, sy + sh * 0.6f)
        close()
    }
    drawPath(inner, Color(0xFFEE1C25))
    val cx = sx + sw / 2f
    val cw = sw * 0.12f
    drawRect(Color.White, Offset(cx - cw / 2f, sy + sh * 0.12f), GSize(cw, sh * 0.62f))
    drawRect(Color.White, Offset(cx - sw * 0.28f, sy + sh * 0.25f), GSize(sw * 0.56f, cw))
    drawRect(Color.White, Offset(cx - sw * 0.36f, sy + sh * 0.43f), GSize(sw * 0.72f, cw))
    drawCircle(Color(0xFF0B4EA2), radius = sw * 0.17f, center = Offset(cx, sy + sh * 0.86f))
}

/** Britská vlajka (zjednodušená). */
private fun DrawScope.ukFlag() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF012169))
    val white = Color.White
    val red = Color(0xFFC8102E)
    drawLine(white, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.2f)
    drawLine(white, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.2f)
    drawLine(red, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.07f)
    drawLine(red, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.07f)
    drawRect(white, Offset(w / 2f - h * 0.17f, 0f), GSize(h * 0.34f, h))
    drawRect(white, Offset(0f, h / 2f - h * 0.17f), GSize(w, h * 0.34f))
    drawRect(red, Offset(w / 2f - h * 0.1f, 0f), GSize(h * 0.2f, h))
    drawRect(red, Offset(0f, h / 2f - h * 0.1f), GSize(w, h * 0.2f))
}
