package sk.vespal.filesc8.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.vespal.filesc8.Cat
import sk.vespal.filesc8.Repo
import sk.vespal.filesc8.Sug
import sk.vespal.filesc8.tr

sealed interface Screen {
    data class Category(val cat: Cat) : Screen
    data class Suggestion(val sug: Sug) : Screen
    data object Favorites : Screen
    data object Apps : Screen
}

@Composable
fun AppRoot(hasAccess: Boolean, requestAccess: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (!hasAccess) PermissionScreen(requestAccess) else MainContent()
        // vstavaný prehliadač fotiek a videí – cez celú obrazovku
        if (hasAccess && Viewer.open) ViewerScreen()
    }
}

@Composable
private fun MainContent() {
    run {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val stack = remember { mutableStateListOf<Screen>() }
        fun push(s: Screen) { stack.add(s) }
        fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

        BackHandler(enabled = stack.isNotEmpty()) { pop() }
        BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val top = stack.lastOrNull()
                when (top) {
                    null -> when (tab) {
                        1 -> BrowseScreen(onFavorites = { push(Screen.Favorites) })
                        2 -> CleanScreen(onSug = { push(Screen.Suggestion(it)) })
                        else -> HomeScreen(
                            onCategory = { c -> push(if (c == Cat.APPS) Screen.Apps else Screen.Category(c)) },
                            onClean = { tab = 2 },
                            onFavorites = { push(Screen.Favorites) },
                            onBig = { push(Screen.Suggestion(Sug.BIG)) },
                            onBrowse = { tab = 1 },
                        )
                    }
                    is Screen.Category -> {
                        val c = top.cat
                        FileListScreen(
                            title = c.label, icon = c.icon, color = c.color, tint = c.tint,
                            files = Repo.snap.byCat[c] ?: emptyList(),
                            grid = c == Cat.IMAGES || c == Cat.VIDEOS || c == Cat.SCREENSHOTS,
                            onBack = { pop() },
                            emptyText = tr("Žiadne súbory", "No files"),
                            loading = !Repo.snap.loaded,
                        )
                    }
                    is Screen.Suggestion -> {
                        val s = top.sug
                        FileListScreen(
                            title = s.label, icon = sugIcon(s), color = s.color, tint = s.tint,
                            files = Repo.sugItems(s),
                            grid = s == Sug.OLD_SHOTS,
                            onBack = { pop() },
                            emptyText = tr("Nič na vyčistenie", "Nothing to clean"),
                            loading = s == Sug.DUPES && Repo.dupes == null,
                            header = {
                                Text(
                                    s.desc + tr(". Podrž súbor alebo klikni na „Označiť všetko“.", ". Long-press a file or tap “Select all”."),
                                    fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                            }
                        )
                    }
                    Screen.Favorites -> {
                        val fav = Repo.favorites
                        FileListScreen(
                            title = tr("Obľúbené", "Favorites"), icon = Icons.Rounded.Star, color = C.Star, tint = C.StarSoft,
                            files = Repo.snap.files.filter { it.path in fav },
                            grid = false,
                            onBack = { pop() },
                            emptyText = tr("Podrž súbor a označ ho hviezdičkou", "Long-press a file and star it"),
                        )
                    }
                    Screen.Apps -> AppsScreen(onBack = { pop() })
                }
            }
            if (stack.isEmpty()) BottomNav(tab) { tab = it }
        }
    }
}

@Composable
private fun PermissionScreen(requestAccess: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LangSwitch() }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(C.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.FolderOpen, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Text(
            "FilesC8", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            tr(
                "Aby aplikácia mohla zobraziť, triediť a čistiť tvoje súbory, potrebuje prístup ku všetkým súborom v telefóne.",
                "To show, sort and clean your files, the app needs access to all files on your phone."
            ),
            fontSize = 15.sp, color = C.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
        )
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = C.Green, modifier = Modifier.size(16.dp))
            Text(
                tr("Súbory zostávajú len v telefóne, nič sa neodosiela.", "Files stay on your phone, nothing is uploaded."),
                fontSize = 13.sp, color = C.Green, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(C.Accent).clickable(onClick = requestAccess),
            contentAlignment = Alignment.Center
        ) {
            Text(tr("Povoliť prístup", "Allow access"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(
            tr("V nastaveniach zapni „Povoliť prístup na správu všetkých súborov“.", "In settings, turn on “Allow access to manage all files”."),
            fontSize = 12.sp, color = C.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
        )
    }
}
