package sk.vespal.filesc8.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.vespal.filesc8.FItem
import sk.vespal.filesc8.Repo
import sk.vespal.filesc8.tr
import java.io.File
import kotlin.math.max

/** Stav vstavaného prehliadača fotiek a videí. */
object Viewer {
    var open by mutableStateOf(false)
        private set
    var items by mutableStateOf<List<FItem>>(emptyList())
        private set
    var start by mutableIntStateOf(0)
        private set

    fun show(list: List<FItem>, index: Int) {
        items = list
        start = index
        open = true
    }

    fun close() {
        open = false
    }
}

private fun decodeImage(path: String): Bitmap? = try {
    val src = ImageDecoder.createSource(File(path))
    ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val maxSide = 2560
        val big = max(w, h)
        if (big > maxSide) {
            val f = maxSide.toFloat() / big
            decoder.setTargetSize((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1))
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
} catch (e: Throwable) {
    null
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = remember(Viewer.items) { mutableStateListOf<FItem>().apply { addAll(Viewer.items) } }
    val pager = rememberPagerState(initialPage = Viewer.start.coerceIn(0, max(0, Viewer.items.size - 1)), pageCount = { list.size })
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    BackHandler { Viewer.close() }

    // svetlé ikony v stavovom riadku na čiernom pozadí
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (ctx as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.isAppearanceLightStatusBars = false
        ctl?.isAppearanceLightNavigationBars = false
        onDispose {
            ctl?.isAppearanceLightStatusBars = true
            ctl?.isAppearanceLightNavigationBars = true
        }
    }

    if (list.isEmpty()) {
        Viewer.close()
        return
    }
    val page = pager.currentPage.coerceIn(0, list.size - 1)
    val cur = list[page]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            key = { list[it].path },
        ) { p ->
            val item = list[p]
            if (item.isVideo) {
                VideoPage(item, active = p == pager.currentPage)
            } else {
                ImagePage(item, onTap = { chrome = !chrome }, onZoom = { if (p == pager.currentPage) zoomed = it })
            }
        }

        if (chrome || cur.isVideo) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Späť", "Back"), tint = Color.White) { Viewer.close() }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(cur.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${page + 1} / ${list.size} · ${fmtSize(cur.size)} · ${fmtDate(cur.modified)}",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1
                    )
                }
                RoundIconButton(Icons.Rounded.Share, tr("Zdieľať", "Share"), tint = Color.White) { shareFiles(ctx, listOf(cur)) }
                val fav = Repo.isFav(cur.path)
                RoundIconButton(
                    if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr("Obľúbené", "Favorite"),
                    tint = if (fav) C.Star else Color.White
                ) { Repo.toggleFav(ctx, listOf(cur.path)) }
                RoundIconButton(Icons.Rounded.Delete, tr("Vymazať", "Delete"), tint = Color.White) { askDelete = true }
                RoundIconButton(Icons.Rounded.OpenInNew, tr("Otvoriť v inej aplikácii", "Open in another app"), tint = Color.White) {
                    openExternal(ctx, cur)
                }
            }
        }
    }

    if (askDelete) {
        DeleteDialog(1, cur.size, onConfirm = {
            askDelete = false
            val target = cur
            scope.launch {
                val n = Repo.delete(ctx, listOf(target))
                if (n > 0) {
                    list.remove(target)
                    if (list.isEmpty()) Viewer.close()
                }
            }
        }, onDismiss = { askDelete = false })
    }
}

@Composable
private fun ImagePage(item: FItem, onTap: () -> Unit, onZoom: (Boolean) -> Unit) {
    val img: ImageBitmap? by produceState<ImageBitmap?>(Thumbs.peek(item.path)?.asImageBitmap(), item.path) {
        val full = withContext(Dispatchers.IO) { decodeImage(item.path) }
        if (full != null) value = full.asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun clamp(o: Offset, w: Float, h: Float, s: Float): Offset {
        val mx = w * (s - 1f) / 2f
        val my = h * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(item.path) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoom(false)
                        } else {
                            scale = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((center - tap) * 1.5f, size.width.toFloat(), size.height.toFloat(), 2.5f)
                            onZoom(true)
                        }
                    }
                )
            }
            .pointerInput(item.path) {
                // priblíženie dvoma prstami; jedným prstom sa posúva len pri priblížení
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            val ns = (scale * z).coerceIn(1f, 5f)
                            scale = ns
                            offset = if (ns > 1f) clamp(offset + pan, size.width.toFloat(), size.height.toFloat(), ns) else Offset.Zero
                            onZoom(ns > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val b = img
        if (b != null) {
            Image(
                b, contentDescription = item.name,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(item: FItem, active: Boolean) {
    if (!active) {
        // neaktívna strana – len náhľad (prehráva sa vždy iba jedno video)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FileThumb(item, Modifier.fillMaxWidth().aspectRatio(1f), iconSize = 48.dp)
        }
        return
    }
    val ctx = LocalContext.current
    val player = remember(item.path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(item.path))))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = true
                controllerShowTimeoutMs = 2500
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowNextButton(false)
                setShowPreviousButton(false)
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize().navigationBarsPadding()
    )
}
