package com.bitdroid.clips.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitdroid.clips.LoginActivity
import com.bitdroid.clips.data.Account
import com.bitdroid.clips.data.ChannelRef
import com.bitdroid.clips.data.Entry
import com.bitdroid.clips.data.Library
import com.bitdroid.clips.data.Paged
import com.bitdroid.clips.data.Repo
import com.bitdroid.clips.data.Sync
import com.bitdroid.clips.data.VideoRef
import com.bitdroid.clips.data.compact
import com.bitdroid.clips.data.pickImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun openSignIn(context: Context) {
    context.startActivity(Intent(context, LoginActivity::class.java))
}

// ================================================================== home (For You / Following)

@Composable
fun HomeScreen() {
    var tab by rememberSaveable { mutableStateOf(1) } // 0 Following, 1 For You
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val list = if (tab == 0) Feeds.following else Feeds.forYou
        androidx.compose.runtime.key(tab) { ClipPager(list) }
        // TikTok's top switch, with a cookie under the one you're on.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp, start = 14.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CookieButton(Icons.Rounded.Refresh, "Refresh", { scope.launch { list.fresh() } }, size = 40.dp, shape = Shapes.Flower, bg = Color.Black.copy(alpha = 0.25f), tint = Color.White)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                listOf("Following", "For You").forEachIndexed { i, label ->
                    val on = i == tab
                    Column(Modifier.padding(horizontal = 12.dp).squish(scale = 0.94f) { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = body(17f, if (on) Color.White else Color.White.copy(alpha = 0.62f), if (on) FontWeight.ExtraBold else FontWeight.SemiBold))
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(if (on) 10.dp else 0.dp).clip(Shapes.Sunny).background(Bit.accent))
                    }
                }
            }
            CookieButton(Icons.Rounded.Search, "Search", { Nav.tab(1) }, size = 40.dp, shape = Shapes.Sunny, bg = Color.Black.copy(alpha = 0.25f), tint = Color.White)
        }
    }
}

/** Full-screen swiping through a grid you tapped into. */
@Composable
fun ViewerScreen(title: String) {
    val list = Feeds.viewer ?: return
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ClipPager(list)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CookieButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { Nav.back() }, size = 42.dp, shape = CircleShape, bg = Color.Black.copy(alpha = 0.3f), tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(title, style = body(16f, Color.White, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ================================================================== grids

@Composable
fun ClipCell(v: VideoRef, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).background(Bit.raised).squish(scale = 0.95f, onClick = onClick)) {
        Img(v.thumb, Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
        val views = v.viewsText?.substringBefore(" ") ?: v.views.takeIf { it >= 0 }?.let { compact(it) }
        Row(Modifier.align(Alignment.BottomStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            if (views != null) Text(views, style = mono(11f, Color.White))
        }
    }
}

fun LazyGridScope.clipCells(title: String, list: List<VideoRef>, paged: Paged? = null) {
    itemsIndexed(list, key = { _, v -> "g" + v.id }) { i, v -> ClipCell(v) { Feeds.open(title, list, i, paged) } }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Grid(content: LazyGridScope.() -> Unit, onNearEnd: (suspend () -> Unit)? = null) {
    val state = rememberLazyGridState()
    if (onNearEnd != null) {
        LaunchedEffect(state) {
            snapshotFlow {
                val info = state.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
            }.collect { if (it) onNearEnd() }
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(), state = state,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomRoom()),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

// ================================================================== discover + search

private val hashtags = listOf("funny", "satisfying", "gaming", "music", "cooking", "dance", "animals", "tech", "sports", "art", "travel", "cars", "fitness", "comedy", "science", "diy")

@Composable
fun DiscoverScreen() {
    var tag by rememberSaveable { mutableStateOf(hashtags.first()) }
    SearchResults(tag, title = null) {
        Column {
            Header("Discover", "Tap the clover to search")
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hashtags) { h -> Chip("#$h", h == tag, { tag = h }) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SearchScreen(query: String) {
    SearchResults(query, title = query) { Header(query, "Clips", onBack = { Nav.back() }) }
}

@Composable
private fun SearchResults(query: String, title: String?, header: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val list = rememberPaged(query) { Repo.searchClips(query) }
    val videos = list.items.mapNotNull { (it as? Entry.Video)?.v }
    Grid({
        full(header)
        when {
            videos.isEmpty() && list.loading -> full { CenterLoader() }
            videos.isEmpty() && list.error != null -> full { ErrorState(list.error!!) { scope.launch { list.refresh() } } }
            videos.isEmpty() -> full { MessageState(Icons.Rounded.SearchOff, "No clips found", "Try another word.") }
            else -> {
                clipCells(title ?: "#$query", videos)
                if (list.loading) full { CenterLoader() }
            }
        }
    }, onNearEnd = { list.more() })
}

/** Shown above everything while the tab bar is in search mode. */
@Composable
fun SearchPanel(query: String, onPick: (String) -> Unit, onFill: (String) -> Unit) {
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(query) {
        delay(180)
        suggestions = runCatching { Repo.suggestions(query) }.getOrDefault(emptyList())
    }
    val recent = Library.searches.filter { query.isBlank() || it.contains(query, true) }.take(8)
    LazyColumn(Modifier.fillMaxSize().background(Bit.bg.copy(alpha = 0.97f)), contentPadding = PaddingValues(bottom = bottomRoom())) {
        item { Header("Search", "Find clips") }
        items(recent, key = { "r$it" }) { q -> SearchLine(Icons.Rounded.History, q, { onPick(q) }, { onFill(q) }, onRemove = { Library.removeSearch(q) }) }
        items(suggestions.filter { it !in recent }, key = { "s$it" }) { q -> SearchLine(Icons.Rounded.Search, q, { onPick(q) }, { onFill(q) }) }
    }
}

@Composable
private fun SearchLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit, onFill: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(20.dp)).squish(scale = 0.98f, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Bit.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = body(16f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onRemove != null) Icon(Icons.Rounded.Close, "Forget", tint = Bit.dim, modifier = Modifier.size(36.dp).squish(onClick = onRemove).padding(8.dp))
        Icon(Icons.Rounded.NorthWest, "Edit", tint = Bit.dim, modifier = Modifier.size(36.dp).squish(onClick = onFill).padding(8.dp))
    }
}

// ================================================================== following (channels)

@Composable
fun FollowingScreen() {
    val subs = Library.subscriptions
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomRoom())) {
        item { Header("Following", "${subs.size} creators") }
        if (subs.isEmpty()) item { MessageState(Icons.Rounded.Group, "Not following anyone yet", "Tap the + on a creator's picture while you watch.") }
        items(subs.toList(), key = { it.url }) { c ->
            ChannelRow(c, { Nav.go(Screen.Channel(c.url, c.name)) }) { FollowButton(c) }
        }
    }
}

@Composable
fun FollowButton(c: ChannelRef, modifier: Modifier = Modifier) {
    val on = Library.isSubscribed(c.url)
    Text(
        if (on) "Following" else "Follow",
        style = body(14f, if (on) Bit.text else Bit.onAccent, FontWeight.Bold),
        modifier = modifier.squish { Sync.subscribe(c) }.clip(RoundedCornerShape(16.dp)).background(if (on) Bit.raised else Bit.accent).padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** A creator's page: big cookie avatar, follow button, and their clips in a grid. */
@Composable
fun ChannelScreen(url: String, fallbackName: String) {
    var data by remember(url) { mutableStateOf<Pair<Repo.Channel, Paged>?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        error = null
        try { data = Repo.channelClips(url) } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "${e.javaClass.simpleName}: ${e.message}"
        }
    }
    LaunchedEffect(url) { if (data == null) load() }
    val info = data?.first?.info
    val ref = ChannelRef(url, info?.name ?: fallbackName, info?.avatars?.let { pickImage(it, 200) }, info?.subscriberCount ?: -1)
    val clips = data?.second?.items?.mapNotNull { (it as? Entry.Video)?.v }.orEmpty()
    Grid({
        full {
            Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    CookieButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { Nav.back() }, size = 44.dp, shape = CircleShape)
                }
                Box(Modifier.size(112.dp).clip(Shapes.forKey(url)).background(Bit.soft).padding(5.dp)) { Avatar(ref.avatar, url, 102.dp) }
                Spacer(Modifier.height(10.dp))
                Text("@" + ref.name.replace(" ", ""), style = display(19f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat(if (ref.subscribers >= 0) compact(ref.subscribers) else "–", "Followers")
                    Stat(if (clips.isNotEmpty()) "${clips.size}${if (data?.second?.hasMore == true) "+" else ""}" else "–", "Clips")
                }
                Spacer(Modifier.height(14.dp))
                FollowButton(ref, Modifier.width(180.dp))
                Spacer(Modifier.height(16.dp))
            }
        }
        when {
            error != null -> full { ErrorState(error!!) { scope.launch { load() } } }
            data == null -> full { CenterLoader() }
            clips.isEmpty() -> full { MessageState(Icons.Rounded.SearchOff, "No clips", "This creator hasn't posted Shorts.") }
            else -> clipCells("@" + ref.name.replace(" ", ""), clips, data?.second)
        }
    })
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = display(18f))
        Text(label, style = body(12.5f, Bit.dim))
    }
}

// ================================================================== profile

@Composable
fun ProfileScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val lists = listOf(
        Triple(Icons.Rounded.Favorite, "Liked", Library.liked.filter { it.short || it.url.contains("/shorts/") }.ifEmpty { Library.liked.toList() }),
        Triple(Icons.Rounded.Bookmark, "Saved", Library.playlists.firstOrNull { it.id == Library.WATCH_LATER }?.items.orEmpty()),
        Triple(Icons.Rounded.History, "History", Library.history.toList()),
    )
    val (_, label, clips) = lists[tab]
    Grid({
        full {
            Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp), horizontalArrangement = Arrangement.End) {
                    CookieButton(Icons.Rounded.Settings, "Settings", { Nav.go(Screen.Settings) }, shape = Shapes.Sunny, bg = Bit.soft, tint = Bit.onSoft)
                }
                Box(Modifier.size(112.dp).clip(Shapes.Ripple).background(Bit.soft).padding(5.dp), contentAlignment = Alignment.Center) {
                    if (Account.photo != null) Img(Account.photo, Modifier.fillMaxSize(), Shapes.Ripple)
                    else Icon(Icons.Rounded.AccountCircle, null, tint = Bit.onSoft, modifier = Modifier.size(60.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(Account.name ?: "You", style = display(20f))
                Account.handle?.let { Text(it, style = body(13.5f, Bit.dim)) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.squish { Nav.tab(2) }) { Stat("${Library.subscriptions.size}", "Following") }
                    Stat("${Library.liked.size}", "Likes")
                    Stat("${lists[1].third.size}", "Saved")
                }
                if (!Account.signedIn) {
                    Spacer(Modifier.height(12.dp))
                    PillButton("Sign in", { openSignIn(context) }, icon = Icons.Rounded.AccountCircle)
                }
                Spacer(Modifier.height(16.dp))
                // Tab strip with a sliding cookie marker
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    lists.forEachIndexed { i, (icon, l, _) ->
                        val on = i == tab
                        Column(Modifier.weight(1f).squish(scale = 0.94f) { tab = i }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, l, tint = if (on) Bit.accent else Bit.dim, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.width(if (on) 34.dp else 0.dp).height(4.dp).clip(CircleShape).background(Bit.accent))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (clips.isEmpty()) {
            full {
                MessageState(
                    lists[tab].first, "Nothing $label yet".replace("Nothing Liked", "No liked clips").replace("Nothing Saved", "Nothing saved").replace("Nothing History", "No history"),
                    when (tab) { 0 -> "Double-tap a clip to like it."; 1 -> "Tap the bookmark on a clip to save it."; else -> "Clips you watch show up here." },
                )
            }
        } else clipCells(label, clips)
    })
}

