package sk.vespal.filesc8

import android.app.usage.StorageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Kategórie súborov – každá má vlastnú farbu, ktorá sa drží v celej aplikácii. */
enum class Cat(val sk: String, val en: String, val color: Color, val tint: Color, val icon: ImageVector) {
    DOWNLOADS("Stiahnuté", "Downloads", Color(0xFF2563EB), Color(0xFFE8EFFE), Icons.Rounded.FileDownload),
    IMAGES("Obrázky", "Images", Color(0xFFDB2777), Color(0xFFFCE8F2), Icons.Rounded.Image),
    VIDEOS("Videá", "Videos", Color(0xFF7C3AED), Color(0xFFF0EAFE), Icons.Rounded.Videocam),
    AUDIO("Zvukové", "Audio", Color(0xFFEA580C), Color(0xFFFEEEE4), Icons.Rounded.MusicNote),
    DOCS("Dokumenty", "Documents", Color(0xFF0D9488), Color(0xFFE2F5F3), Icons.Rounded.Description),
    APPS("Aplikácie", "Apps", Color(0xFF16A34A), Color(0xFFE5F6EA), Icons.Rounded.Android),
    ARCHIVES("Archívy a APK", "Archives & APK", Color(0xFFB45309), Color(0xFFFBF0E1), Icons.Rounded.Archive),
    SCREENSHOTS("Snímky obrazovky", "Screenshots", Color(0xFF0284C7), Color(0xFFE3F2FB), Icons.Rounded.Screenshot);

    val label: String get() = tr(sk, en)
}

data class FItem(
    val id: Long,
    val name: String,
    val path: String,
    val size: Long,
    val modified: Long,
    val mime: String,
    val duration: Long = 0,
) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
    val isImage: Boolean get() = mime.startsWith("image/")
    val isVideo: Boolean get() = mime.startsWith("video/")
    val isAudio: Boolean get() = mime.startsWith("audio/")
    val folder: String get() = path.substringBeforeLast('/').substringAfterLast('/')
}

data class AppItem(
    val pkg: String,
    val label: String,
    val apkSize: Long,
    val system: Boolean,
)

class Snap(
    val files: List<FItem> = emptyList(),
    val total: Long = 0,
    val free: Long = 0,
    val apps: List<AppItem> = emptyList(),
    val loaded: Boolean = false,
) {
    val used: Long get() = (total - free).coerceAtLeast(0)

    val byCat: Map<Cat, List<FItem>> = Cat.entries.associateWith { c ->
        if (c == Cat.APPS) emptyList() else files.filter { it.inCat(c) }
    }

    fun catSize(c: Cat): Long =
        if (c == Cat.APPS) apps.sumOf { it.apkSize } else (byCat[c] ?: emptyList()).sumOf { it.size }

    fun catCount(c: Cat): Int = if (c == Cat.APPS) apps.size else (byCat[c]?.size ?: 0)

    fun withoutPaths(gone: Set<String>, freed: Long) =
        Snap(files.filterNot { it.path in gone }, total, free + freed, apps, loaded)
}

val DOC_EXT = setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "epub", "md")
val ARC_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "apk", "apks", "xapk")

fun FItem.isScreenshot(): Boolean =
    path.contains("/Screenshots/", ignoreCase = true) || path.contains("/ScreenRecord", ignoreCase = true)

fun FItem.inCat(c: Cat): Boolean = when (c) {
    Cat.DOWNLOADS -> path.contains("/Download/", ignoreCase = true)
    Cat.IMAGES -> isImage
    Cat.VIDEOS -> isVideo
    Cat.AUDIO -> isAudio
    Cat.DOCS -> ext in DOC_EXT
    Cat.ARCHIVES -> ext in ARC_EXT
    Cat.SCREENSHOTS -> (isImage || isVideo) && isScreenshot()
    Cat.APPS -> false
}

/** Kategória podľa typu súboru – pre ikonu a farbu. */
fun FItem.typeCat(): Cat? = when {
    isImage -> Cat.IMAGES
    isVideo -> Cat.VIDEOS
    isAudio -> Cat.AUDIO
    ext in DOC_EXT -> Cat.DOCS
    ext in ARC_EXT -> Cat.ARCHIVES
    else -> null
}

fun mimeOf(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}

fun fileToItem(f: File): FItem = FItem(-1, f.name, f.absolutePath, f.length(), f.lastModified(), mimeOf(f.name))

/** Návrhy na vyčistenie. */
enum class Sug(val sk: String, val en: String, val dsk: String, val den: String, val color: Color, val tint: Color) {
    BIG("Veľké súbory", "Large files", "Súbory nad 100 MB", "Files over 100 MB", Color(0xFF7C3AED), Color(0xFFF0EAFE)),
    DUPES("Duplicitné súbory", "Duplicate files", "Rovnaké kópie – originál ostane", "Identical copies – the original stays", Color(0xFFDB2777), Color(0xFFFCE8F2)),
    OLD_SHOTS("Staré snímky obrazovky", "Old screenshots", "Staršie ako 30 dní", "Older than 30 days", Color(0xFF0284C7), Color(0xFFE3F2FB)),
    APKS("Inštalačné súbory APK", "APK installers", "Aplikácie sú už nainštalované", "Apps are already installed", Color(0xFFB45309), Color(0xFFFBF0E1)),
    OLD_DOWNLOADS("Staré stiahnuté súbory", "Old downloads", "V priečinku Download dlhšie ako 90 dní", "In Download for over 90 days", Color(0xFF2563EB), Color(0xFFE8EFFE));

    val label: String get() = tr(sk, en)
    val desc: String get() = tr(dsk, den)
}

object Repo {
    var snap by mutableStateOf(Snap())
        private set
    var loading by mutableStateOf(false)
        private set
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Duplicity – počítajú sa až na požiadanie (čítajú súbory). null = ešte nepočítané. */
    var dupes by mutableStateOf<List<FItem>?>(null)
        private set
    var dupesRunning by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var favLoaded = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("filesc8", Context.MODE_PRIVATE)

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        if (!favLoaded) {
            favLoaded = true
            favorites = prefs(app).getStringSet("fav", emptySet())?.toSet() ?: emptySet()
        }
        if (job?.isActive == true) return
        loading = true
        job = scope.launch {
            val s = try {
                scan(app)
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (s != null) {
                    snap = s
                    dupes = null
                }
                loading = false
            }
        }
    }

    private fun scan(ctx: Context): Snap {
        val list = ArrayList<FItem>(4096)
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.MediaColumns.DURATION,
        )
        val sel = "${MediaStore.Files.FileColumns.SIZE} > 0"
        ctx.contentResolver.query(uri, proj, sel, null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val iData = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val iDur = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            while (c.moveToNext()) {
                val path = c.getString(iData) ?: continue
                if (path.contains("/.")) continue // skryté súbory a priečinky
                val name = c.getString(iName) ?: path.substringAfterLast('/')
                val mime = c.getString(iMime)
                if (mime == null && !name.contains('.')) continue // priečinky
                list += FItem(
                    id = c.getLong(iId),
                    name = name,
                    path = path,
                    size = c.getLong(iSize),
                    modified = c.getLong(iDate) * 1000L,
                    mime = mime ?: mimeOf(name),
                    duration = if (iDur >= 0 && !c.isNull(iDur)) c.getLong(iDur) else 0L,
                )
            }
        }

        var total = 0L
        var free = 0L
        try {
            val ssm = ctx.getSystemService(StorageStatsManager::class.java)
            total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
        } catch (e: Exception) {
            val st = StatFs(Environment.getExternalStorageDirectory().path)
            total = st.totalBytes
            free = st.availableBytes
        }

        val apps = loadApps(ctx)
        return Snap(list, total, free, apps, loaded = true)
    }

    private fun loadApps(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val out = ArrayList<AppItem>()
        for (ri in resolved) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            if (!seen.add(ai.packageName)) continue
            if (ai.packageName == ctx.packageName) continue
            var size = File(ai.sourceDir ?: "").length()
            ai.splitSourceDirs?.forEach { size += File(it).length() }
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            out += AppItem(ai.packageName, ai.loadLabel(pm).toString(), size, system)
        }
        return out.sortedByDescending { it.apkSize }
    }

    // ---------- obľúbené ----------

    fun isFav(path: String) = path in favorites

    fun toggleFav(ctx: Context, paths: Collection<String>) {
        val allFav = paths.isNotEmpty() && paths.all { it in favorites }
        favorites = if (allFav) favorites - paths.toSet() else favorites + paths
        prefs(ctx).edit().putStringSet("fav", favorites).apply()
    }

    // ---------- mazanie ----------

    suspend fun delete(ctx: Context, items: List<FItem>): Int = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val gone = HashSet<String>()
        var freed = 0L
        for (it in items) {
            val f = File(it.path)
            val ok = if (f.isDirectory) f.deleteRecursively() else (f.delete() || !f.exists())
            if (!ok) continue
            gone += it.path
            freed += it.size
            try {
                if (it.id >= 0) {
                    app.contentResolver.delete(
                        ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), it.id), null, null
                    )
                } else {
                    MediaScannerConnection.scanFile(app, arrayOf(it.path), null, null)
                }
            } catch (_: Exception) {
            }
        }
        withContext(Dispatchers.Main) {
            snap = snap.withoutPaths(gone, freed)
            dupes = dupes?.filterNot { it.path in gone }
            if (favorites.any { it in gone }) {
                favorites = favorites - gone
                prefs(app).edit().putStringSet("fav", favorites).apply()
            }
        }
        gone.size
    }

    // ---------- návrhy na čistenie ----------

    fun sugItems(s: Sug, sn: Snap = snap): List<FItem> {
        val now = System.currentTimeMillis()
        val day = 24L * 3600 * 1000
        return when (s) {
            Sug.BIG -> sn.files.filter { it.size >= 100L * 1024 * 1024 }.sortedByDescending { it.size }
            Sug.DUPES -> dupes ?: emptyList()
            Sug.OLD_SHOTS -> (sn.byCat[Cat.SCREENSHOTS] ?: emptyList()).filter { now - it.modified > 30 * day }
            Sug.APKS -> sn.files.filter { it.ext == "apk" || it.ext == "apks" || it.ext == "xapk" }
            Sug.OLD_DOWNLOADS -> (sn.byCat[Cat.DOWNLOADS] ?: emptyList()).filter { now - it.modified > 90 * day }
        }
    }

    /** Súčet všetkého, čo sa dá uvoľniť (bez duplicít, ak ešte nie sú spočítané). */
    fun freeable(sn: Snap = snap): Long {
        val all = HashMap<String, Long>()
        for (s in Sug.entries) for (f in sugItems(s, sn)) all[f.path] = f.size
        return all.values.sum()
    }

    fun computeDupes() {
        if (dupesRunning || dupes != null) return
        dupesRunning = true
        val files = snap.files
        scope.launch {
            val result = ArrayList<FItem>()
            try {
                val groups = files.filter { it.size >= 50_000 }.groupBy { it.size }.values.filter { it.size > 1 }
                for (g in groups) {
                    val byHash = g.groupBy { quickHash(it.path) }
                    for ((h, same) in byHash) {
                        if (h == null || same.size < 2) continue
                        result.addAll(same.sortedBy { it.modified }.drop(1))
                    }
                }
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) {
                dupes = result.sortedByDescending { it.size }
                dupesRunning = false
            }
        }
    }

    private fun quickHash(path: String): String? = try {
        RandomAccessFile(path, "r").use { raf ->
            val md = MessageDigest.getInstance("MD5")
            val buf = ByteArray(65536)
            val len = raf.length()
            var n = raf.read(buf)
            if (n > 0) md.update(buf, 0, n)
            if (len > 131072) {
                raf.seek(len - 65536)
                n = raf.read(buf)
                if (n > 0) md.update(buf, 0, n)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) {
        null
    }
}
