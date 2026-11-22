package sk.vespal.filesc8.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.vespal.filesc8.AppItem
import sk.vespal.filesc8.Cat
import sk.vespal.filesc8.Repo
import sk.vespal.filesc8.tr

private fun uninstall(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

private fun appInfo(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var chip by rememberSaveable { mutableIntStateOf(0) }
    val all = Repo.snap.apps
    val user = all.filter { !it.system }
    val sys = all.filter { it.system }
    val list = if (chip == 0) user else sys
    val c = Cat.APPS

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(
            title = c.label,
            subtitle = "${fmtCount(all.size)} · ${fmtSize(all.sumOf { it.apkSize })}",
            icon = c.icon, color = c.color, onBack = onBack
        )
        ChipsRow(
            listOf(tr("Stiahnuté", "Installed") + " " + user.size, tr("Systémové", "System") + " " + sys.size),
            chip, c.color
        ) { chip = it }
        Text(
            tr("Veľkosť = inštalačný balík aplikácie (bez dát a cache).", "Size = app package (without data and cache)."),
            fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.pkg }) { a -> AppRow(a, onClick = { appInfo(ctx, a.pkg) }, onUninstall = { uninstall(ctx, a.pkg) }) }
        }
    }
}

@Composable
private fun AppRow(a: AppItem, onClick: () -> Unit, onUninstall: () -> Unit) {
    val ctx = LocalContext.current
    val icon: ImageBitmap? by produceState<ImageBitmap?>(null, a.pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(128, 128).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(C.Surface).border(1.dp, C.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ic = icon
        if (ic != null) Image(ic, null, Modifier.size(44.dp))
        else SoftTile(Icons.Rounded.Android, Cat.APPS.color, Cat.APPS.tint, size = 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(a.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtSize(a.apkSize), fontSize = 12.sp, color = Cat.APPS.color, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        if (!a.system) {
            val bshape = RoundedCornerShape(12.dp)
            Text(
                tr("Odinštalovať", "Uninstall"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Red,
                modifier = Modifier.clip(bshape).border(1.dp, C.Line, bshape).clickable(onClick = onUninstall)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}
