package sk.vespal.filesc8.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import sk.vespal.filesc8.Cat
import sk.vespal.filesc8.FItem
import sk.vespal.filesc8.tr
import java.io.File
import java.util.Locale

/** Hudobný prehrávač – hrá ďalej aj pri prechádzaní aplikáciou (mini lišta dole). */
object Audio {
    private var player: ExoPlayer? = null

    var queue by mutableStateOf<List<FItem>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    /** 0 = bez opakovania, 1 = celý zoznam, 2 = jedna skladba */
    var repeat by mutableIntStateOf(0)
        private set
    var showFull by mutableStateOf(false)

    val current: FItem? get() = queue.getOrNull(index)
    val position: Long get() = player?.currentPosition ?: 0L

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            index = player?.currentMediaItemIndex ?: 0
            duration = 0L
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val d = player?.duration ?: 0L
            if (d > 0) duration = d
        }
    }

    fun play(ctx: Context, list: List<FItem>, start: Int) {
        val p = player ?: ExoPlayer.Builder(ctx.applicationContext).build().also {
            it.addListener(listener)
            player = it
        }
        queue = list
        index = start
        duration = 0L
        p.setMediaItems(list.map { MediaItem.fromUri(Uri.fromFile(File(it.path))) }, start, 0L)
        p.shuffleModeEnabled = shuffle
        p.repeatMode = repeatModeOf(repeat)
        p.prepare()
        p.play()
        showFull = true
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
            p.play()
        }
    }

    fun next() {
        val p = player ?: return
        if (p.hasNextMediaItem()) p.seekToNextMediaItem() else p.seekTo(0, 0L)
    }

    fun prev() {
        val p = player ?: return
        // ako v bežných prehrávačoch: po 3 s od začiatku skočí na začiatok skladby
        if (p.currentPosition > 3000 || !p.hasPreviousMediaItem()) p.seekTo(0L) else p.seekToPreviousMediaItem()
    }

    fun playAt(i: Int) {
        val p = player ?: return
        p.seekTo(i, 0L)
        p.play()
    }

    fun seek(ms: Long) {
        player?.seekTo(ms)
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        player?.shuffleModeEnabled = shuffle
    }

    fun cycleRepeat() {
        repeat = (repeat + 1) % 3
        player?.repeatMode = repeatModeOf(repeat)
    }

    private fun repeatModeOf(r: Int) = when (r) {
        1 -> Player.REPEAT_MODE_ALL
        2 -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

    /** Zastaví hudbu a zavrie prehrávač. */
    fun stop() {
        player?.removeListener(listener)
        player?.release()
        player = null
        queue = emptyList()
        playing = false
        showFull = false
    }

    /** Aktualizuje dĺžku skladby, ak ju prehrávač ešte nemal. */
    fun pollDuration() {
        val d = player?.duration ?: 0L
        if (d > 0 && d != duration) duration = d
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

private val Orange = Cat.AUDIO.color
private val OrangeSoft = Cat.AUDIO.tint

@Composable
private fun CircleBtn(icon: ImageVector, label: String, size: Dp, bg: Color, tint: Color, iconSize: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, color: Color, bg: Color, border: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.height(56.dp).clip(shape).background(bg).border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** Celoobrazovkový prehrávač hudby. */
@Composable
fun AudioScreen() {
    val cur = Audio.current ?: return
    val ctx = LocalContext.current
    var nearby by remember { mutableStateOf(false) }
    BackHandler { Audio.showFull = false }
    if (nearby) NearbyDialog(listOf(cur)) { nearby = false }

    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) pos = Audio.position
            Audio.pollDuration()
            delay(250)
        }
    }
    val dur = Audio.duration.coerceAtLeast(1L)
    val listState = rememberLazyListState()
    LaunchedEffect(Audio.index) { listState.animateScrollToItem(Audio.index.coerceAtLeast(0)) }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Rounded.KeyboardArrowDown, tr("Zmenšiť", "Minimize")) { Audio.showFull = false }
            Text(
                tr("Prehráva sa", "Now playing"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            RoundIconButton(Icons.Rounded.Close, tr("Zastaviť a zavrieť", "Stop and close")) { Audio.stop() }
        }

        // akcie: zdieľať, poslať do blízkeho zariadenia, prehrať v BT reproduktore
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionPill(Icons.Rounded.Share, tr("Zdieľať", "Share"), Color(0xFF15803D), Color(0xFFE7F6EC), Color(0xFFBFE5CB), Modifier.weight(1f)) {
                shareFiles(ctx, listOf(cur))
            }
            ActionPill(Icons.Rounded.Wifi, "Wi‑Fi / BT", Color(0xFF1D4ED8), Color(0xFFE8EFFE), Color(0xFFC3D4FB), Modifier.weight(1f)) {
                nearby = true
            }
            ActionPill(Icons.Rounded.Speaker, tr("BT reproduktor", "BT speaker"), Color(0xFF6D28D9), Color(0xFFF0EAFE), Color(0xFFD7C8FB), Modifier.weight(1f)) {
                openOutputSwitcher(ctx)
            }
        }

        // obal albumu
        // menší obal, aby bolo dole vidieť viac skladieb
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(170.dp).clip(RoundedCornerShape(26.dp)).background(OrangeSoft),
                contentAlignment = Alignment.Center
            ) {
                FileThumb(cur, Modifier.fillMaxSize(), iconSize = 72.dp)
            }
        }

        Text(
            cur.name.substringBeforeLast('.'), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp)
        )
        Text(
            "${cur.folder} · ${Audio.index + 1} / ${Audio.queue.size}", fontSize = 13.sp, color = C.Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )

        // posuvník
        Slider(
            value = if (dragging) dragValue else (pos.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                Audio.seek((dragValue * dur).toLong())
                pos = (dragValue * dur).toLong()
                dragging = false
            },
            colors = SliderDefaults.colors(thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = OrangeSoft),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Text(fmtTime(if (dragging) (dragValue * dur).toLong() else pos), fontSize = 12.sp, color = C.Muted, modifier = Modifier.weight(1f))
            Text(if (Audio.duration > 0) fmtTime(Audio.duration) else "--:--", fontSize = 12.sp, color = C.Muted)
        }

        // ovládanie
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleBtn(
                Icons.Rounded.Shuffle, tr("Náhodne", "Shuffle"), 44.dp,
                if (Audio.shuffle) OrangeSoft else Color.Transparent, if (Audio.shuffle) Orange else C.Muted, 22.dp
            ) { Audio.toggleShuffle() }
            CircleBtn(Icons.Rounded.SkipPrevious, tr("Predchádzajúca", "Previous"), 56.dp, Color.Transparent, C.Ink, 34.dp) { Audio.prev() }
            CircleBtn(
                if (Audio.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (Audio.playing) tr("Pauza", "Pause") else tr("Prehrať", "Play"), 72.dp, Orange, Color.White, 40.dp
            ) { Audio.toggle() }
            CircleBtn(Icons.Rounded.SkipNext, tr("Ďalšia", "Next"), 56.dp, Color.Transparent, C.Ink, 34.dp) { Audio.next() }
            CircleBtn(
                if (Audio.repeat == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, tr("Opakovať", "Repeat"), 44.dp,
                if (Audio.repeat > 0) OrangeSoft else Color.Transparent, if (Audio.repeat > 0) Orange else C.Muted, 22.dp
            ) { Audio.cycleRepeat() }
        }

        // zoznam skladieb – ťuknutím prepneš pesničku
        Text(
            tr("Zoznam skladieb", "Playlist"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Muted,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
        )
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            itemsIndexed(Audio.queue, key = { i, f -> "$i${f.path}" }) { i, f ->
                val on = i == Audio.index
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (on) OrangeSoft else Color.Transparent)
                        .clickable { Audio.playAt(i) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (on && Audio.playing) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote, null,
                        tint = if (on) Orange else C.Faint, modifier = Modifier.size(20.dp)
                    )
                    Text(
                        f.name.substringBeforeLast('.'), fontSize = 14.sp, color = if (on) Orange else C.Ink,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    )
                    Text(fmtSize(f.size), fontSize = 12.sp, color = C.Muted)
                }
            }
        }
    }
}

/** Mini lišta prehrávača – zobrazí sa, keď hudba hrá a celý prehrávač je zmenšený. */
@Composable
fun MiniPlayer() {
    val cur = Audio.current ?: return
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = Audio.position
            Audio.pollDuration()
            delay(500)
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(shape)
            .background(C.Surface).border(1.dp, C.Line, shape).clickable { Audio.showFull = true }
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(cur, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), iconSize = 20.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(cur.name.substringBeforeLast('.'), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Audio.index + 1} / ${Audio.queue.size}", fontSize = 11.sp, color = C.Muted)
            }
            CircleBtn(
                if (Audio.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (Audio.playing) tr("Pauza", "Pause") else tr("Prehrať", "Play"), 44.dp, Orange, Color.White, 24.dp
            ) { Audio.toggle() }
            CircleBtn(Icons.Rounded.SkipNext, tr("Ďalšia", "Next"), 44.dp, Color.Transparent, C.Ink, 26.dp) { Audio.next() }
            CircleBtn(Icons.Rounded.Close, tr("Zastaviť", "Stop"), 40.dp, Color.Transparent, C.Muted, 20.dp) { Audio.stop() }
        }
        val frac = if (Audio.duration > 0) (pos.toFloat() / Audio.duration).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(3.dp).background(OrangeSoft)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Orange))
        }
    }
}
