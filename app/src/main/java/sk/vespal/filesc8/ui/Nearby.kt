package sk.vespal.filesc8.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import sk.vespal.filesc8.FItem
import sk.vespal.filesc8.tr
import java.io.File

/** Cieľ na priamy prenos do blízkeho zariadenia. */
class NearbyTarget(val label: String, val component: ComponentName, val wifi: Boolean)

/** Nájde v telefóne Quick Share (Google aj Samsung) a Bluetooth – prenos bez internetu. */
fun findNearbyTargets(ctx: Context, mime: String): List<NearbyTarget> {
    val pm = ctx.packageManager
    val probe = Intent(Intent.ACTION_SEND).setType(mime)
    val list = try {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
    } catch (e: Exception) {
        emptyList()
    }
    val out = ArrayList<NearbyTarget>()
    for (ri in list) {
        val ai = ri.activityInfo ?: continue
        val pkg = ai.packageName
        val label = ri.loadLabel(pm)?.toString() ?: ""
        val lower = label.lowercase()
        val name = when {
            pkg == "com.google.android.gms" && (lower.contains("quick share") || lower.contains("nearby") || lower.contains("nablízku") || lower.contains("rýchle zdieľanie")) -> "Quick Share (Wi‑Fi)"
            pkg.startsWith("com.samsung.android.app.sharelive") -> "Quick Share Samsung (Wi‑Fi)"
            pkg == "com.android.bluetooth" || pkg.contains("bluetooth") -> "Bluetooth"
            else -> null
        } ?: continue
        if (out.none { it.label == name }) out.add(NearbyTarget(name, ComponentName(pkg, ai.name), name.startsWith("Quick")))
    }
    return out.sortedBy { if (it.wifi) 0 else 1 }
}

private fun sendNearby(ctx: Context, target: NearbyTarget, items: List<FItem>) {
    val uris = ArrayList(items.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(it.path)) })
    val mime = if (items.size == 1) items[0].mime else "*/*"
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    send.setType(mime)
        .setComponent(target.component)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(send)
    } catch (e: ActivityNotFoundException) {
        shareFiles(ctx, items)
    } catch (e: SecurityException) {
        shareFiles(ctx, items)
    }
}

/** Dialóg „Poslať do blízkeho zariadenia“ – Quick Share cez Wi‑Fi alebo Bluetooth. */
@Composable
fun NearbyDialog(items: List<FItem>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val mime = if (items.size == 1) items[0].mime else "*/*"
    val targets = remember(mime) { findNearbyTargets(ctx, mime) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        title = { Text(tr("Poslať do blízkeho zariadenia", "Send to a nearby device"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr(
                        "Prenos cez Wi‑Fi alebo Bluetooth bez internetu – do telefónu, tabletu alebo počítača vedľa teba.",
                        "Transfer over Wi‑Fi or Bluetooth without internet – to a phone, tablet or computer next to you."
                    ),
                    color = C.Muted, fontSize = 13.sp
                )
                if (targets.isEmpty()) {
                    Text(
                        tr("V telefóne sa nenašiel Quick Share ani Bluetooth. Použi Zdieľať.", "Quick Share or Bluetooth was not found. Use Share."),
                        color = C.Red, fontSize = 13.sp
                    )
                }
                targets.forEach { t ->
                    ActionRow(if (t.wifi) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth, Color(0xFF1D4ED8), t.label) {
                        sendNearby(ctx, t, items)
                        onDismiss()
                    }
                }
                ActionRow(Icons.Rounded.Share, Color(0xFF15803D), tr("Iné možnosti zdieľania…", "Other sharing options…")) {
                    shareFiles(ctx, items)
                    onDismiss()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Zavrieť", "Close")) } },
    )
}

@Composable
private fun ActionRow(icon: ImageVector, color: Color, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(shape).background(C.Bg).border(1.dp, C.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = C.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }
}

/**
 * Výber, kam má hrať zvuk (Bluetooth reproduktor, slúchadlá…).
 * Android 14+: systémový prepínač výstupu; Android 11–13: panel výstupu médií; inak nastavenia Bluetooth.
 */
fun openOutputSwitcher(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        try {
            if (MediaRouter2.getInstance(ctx).showSystemOutputSwitcher()) return
        } catch (_: Exception) {
        }
    }
    try {
        ctx.startActivity(
            Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    } catch (_: Exception) {
    }
    openBluetoothSettings(ctx)
}

fun openBluetoothSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

private val BT_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_HEARING_AID,
    26, // TYPE_BLE_HEADSET
    27, // TYPE_BLE_SPEAKER
    30, // TYPE_BLE_BROADCAST
)

/** Názov zariadenia na zobrazenie v tlačidle. */
fun btName(d: AudioDeviceInfo): String = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"

/** Prvý pripojený Bluetooth výstup (reproduktor, slúchadlá) alebo null. */
fun findBtOutput(ctx: Context): AudioDeviceInfo? = try {
    ctx.getSystemService(AudioManager::class.java)
        ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull { it.type in BT_OUTPUT_TYPES }
} catch (e: Exception) {
    null
}

/** Reproduktor telefónu. */
fun findPhoneSpeaker(ctx: Context): AudioDeviceInfo? = try {
    ctx.getSystemService(AudioManager::class.java)
        ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
} catch (e: Exception) {
    null
}

/**
 * Pripojený Bluetooth reproduktor / slúchadlá (alebo null, ak nie je pripojené nič).
 * Sleduje pripájanie a odpájanie zariadení v reálnom čase.
 */
@Composable
fun rememberBtOutput(): AudioDeviceInfo? {
    val ctx = LocalContext.current
    var dev by remember { mutableStateOf<AudioDeviceInfo?>(null) }
    DisposableEffect(Unit) {
        val am = ctx.getSystemService(AudioManager::class.java)
        fun update() {
            dev = findBtOutput(ctx)
        }
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = update()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = update()
        }
        update()
        am?.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        onDispose { am?.unregisterAudioDeviceCallback(cb) }
    }
    return dev
}
