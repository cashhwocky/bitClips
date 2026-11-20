package com.bitdroid.clips

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.lifecycleScope
import com.bitdroid.clips.data.Account
import com.bitdroid.clips.data.Library
import com.bitdroid.clips.data.VideoRef
import com.bitdroid.clips.data.videoId
import com.bitdroid.clips.player.ClipPlayback
import com.bitdroid.clips.ui.Bit
import com.bitdroid.clips.ui.BitTheme
import com.bitdroid.clips.ui.ChannelScreen
import com.bitdroid.clips.ui.DiscoverScreen
import com.bitdroid.clips.ui.Feeds
import com.bitdroid.clips.ui.FloatingTabBar
import com.bitdroid.clips.ui.FollowingScreen
import com.bitdroid.clips.ui.HomeScreen
import com.bitdroid.clips.ui.Nav
import com.bitdroid.clips.ui.ProfileScreen
import com.bitdroid.clips.ui.Screen
import com.bitdroid.clips.ui.SearchPanel
import com.bitdroid.clips.ui.SearchScreen
import com.bitdroid.clips.ui.SettingsScreen
import com.bitdroid.clips.ui.Setup
import com.bitdroid.clips.ui.SetupScreen
import com.bitdroid.clips.ui.SheetHost
import com.bitdroid.clips.ui.TabItem
import com.bitdroid.clips.ui.UpdateScreen
import com.bitdroid.clips.ui.ViewerScreen
import com.bitdroid.clips.ui.backdrop
import com.bitdroid.clips.ui.body
import com.bitdroid.clips.update.Updater
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Bit.apply(this)
        Setup.refresh(this)
        if (savedInstanceState == null) handleIntent(intent)
        // Every launch: if GitHub has a newer build, the full-screen update page shows.
        Updater.dismissed = false
        lifecycleScope.launch { Updater.checkOnLaunch(this@MainActivity)?.let { notifyUpdate(it.versionName) } }
        if (Account.signedIn) lifecycleScope.launch { Account.refreshProfile() }
        // Start buffering the first For You clips right away so the feed opens playing.
        lifecycleScope.launch { runCatching { Feeds.warmUp() } }

        setContent {
            BitTheme {
                val haze = remember { HazeState() }
                var searching by remember { mutableStateOf(false) }
                var query by remember { mutableStateOf("") }
                val top = Nav.top

                LaunchedEffect(ClipPlayback.playing) {
                    if (ClipPlayback.playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                BackHandler(enabled = searching) { searching = false; query = "" }
                BackHandler(enabled = !searching && (Nav.stack.size > 1 || top != Screen.Home)) { Nav.back() }

                if (Setup.needed) {
                    // First run (or a permission/sign-in went missing): setup, no way around it.
                    Box(Modifier.fillMaxSize()) {
                        SetupScreen()
                        if (!Updater.dismissed) UpdateScreen()
                    }
                } else Box(Modifier.fillMaxSize().backdrop()) {
                    Box(Modifier.fillMaxSize().hazeSource(haze)) {
                        AnimatedContent(
                            targetState = top to Nav.stack.size,
                            transitionSpec = {
                                val forward = targetState.second >= initialState.second
                                val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                                val fade = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                                if (targetState.second == 1 && initialState.second == 1) {
                                    (fadeIn(fade) + scaleIn(fade, initialScale = 0.97f)) togetherWith fadeOut(fade)
                                } else if (forward) {
                                    (slideInHorizontally(slide) { it / 3 } + fadeIn(fade)) togetherWith (slideOutHorizontally(slide) { -it / 6 } + fadeOut(fade))
                                } else {
                                    (slideInHorizontally(slide) { -it / 6 } + fadeIn(fade)) togetherWith (slideOutHorizontally(slide) { it / 3 } + fadeOut(fade))
                                }
                            },
                            label = "nav",
                        ) { (screen, _) ->
                            when (screen) {
                                Screen.Home -> HomeScreen()
                                Screen.Discover -> DiscoverScreen()
                                Screen.Following -> FollowingScreen()
                                Screen.Profile -> ProfileScreen()
                                Screen.Settings -> SettingsScreen()
                                is Screen.Search -> SearchScreen(screen.query)
                                is Screen.Channel -> ChannelScreen(screen.url, screen.name)
                                is Screen.Viewer -> ViewerScreen(screen.title)
                            }
                        }
                        if (searching) {
                            SearchPanel(query, onPick = { q ->
                                Library.addSearch(q)
                                searching = false
                                query = ""
                                Nav.go(Screen.Search(q))
                            }, onFill = { query = it })
                        }
                    }

                    if (top !is Screen.Viewer) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()) {
                            FloatingTabBar(
                                haze = haze,
                                tabs = listOf(
                                    TabItem(Icons.Rounded.Home, "Home"),
                                    TabItem(Icons.Rounded.Explore, "Discover"),
                                    TabItem(Icons.Rounded.Group, "Following"),
                                    TabItem(Icons.Rounded.Person, "Profile"),
                                ),
                                selected = Nav.tabs.indexOf(Nav.stack.first()).coerceAtLeast(0),
                                onSelect = { i -> searching = false; Nav.tab(i) },
                                searching = searching,
                                onSearch = { searching = !searching; if (!searching) query = "" },
                            ) {
                                val focus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                                Box {
                                    if (query.isEmpty()) Text("Search clips", style = body(17f, Bit.dim))
                                    BasicTextField(
                                        query, { query = it }, singleLine = true,
                                        textStyle = body(17f),
                                        cursorBrush = SolidColor(Bit.accent),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = {
                                            val q = query.trim()
                                            if (q.isNotEmpty()) {
                                                Library.addSearch(q)
                                                searching = false
                                                query = ""
                                                Nav.go(Screen.Search(q))
                                            }
                                        }),
                                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                    )
                                }
                            }
                        }
                    }

                    SheetHost()
                    if (!Updater.dismissed) UpdateScreen()
                }
            }
        }
    }

    /** "Update ready" notification, alongside the full-screen page. */
    private fun notifyUpdate(version: String) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
        nm.createNotificationChannel(android.app.NotificationChannel("updates", "Updates", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = android.app.Notification.Builder(this, "updates")
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("bitClips $version is ready")
            .setContentText("Tap to update")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Bit.apply(this)
        Setup.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        ClipPlayback.pause()
    }

    /** Opens shared YouTube Shorts links straight into the clip viewer. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        val id = videoId(url) ?: return
        val v = VideoRef("https://www.youtube.com/shorts/$id", "", "", null, "https://i.ytimg.com/vi/$id/hqdefault.jpg", null, -1, short = true)
        Feeds.open("Shared clip", listOf(v), 0)
    }
}
