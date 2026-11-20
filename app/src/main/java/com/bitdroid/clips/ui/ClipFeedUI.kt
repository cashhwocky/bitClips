package com.bitdroid.clips.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bitdroid.clips.data.ChannelRef
import com.bitdroid.clips.data.Library
import com.bitdroid.clips.data.Paged
import com.bitdroid.clips.data.Repo
import com.bitdroid.clips.data.Sync
import com.bitdroid.clips.data.VideoRef
import com.bitdroid.clips.data.Entry
import com.bitdroid.clips.data.compact
import com.bitdroid.clips.player.ClipPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/** Room at the bottom for the floating tab bar. */
@Composable
fun bottomRoom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

/** A swipeable list of clips that keeps its place and loads more as you go. */
class ClipList(private val loader: (suspend () -> List<VideoRef>)?, private val personal: Boolean = false) {
    val items = mutableStateListOf<VideoRef>()
    /** Which sign-in session these clips were loaded under. */
    private var loadedSession = -1
    /** Loaded before you signed in (or under another account): start over so it's your feed. */
    val stale: Boolean get() = personal && items.isNotEmpty() && loadedSession != com.bitdroid.clips.data.Account.session
    var page = 0
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun more() {
        val load = loader ?: return
        if (loading) return
        loading = true
        error = null
        try {
            if (items.isEmpty()) loadedSession = com.bitdroid.clips.data.Account.session
            val seen = items.map { it.id }.toHashSet()
            items.addAll(load().filter { it.id !in seen })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            loading = false
        }
    }

    suspend fun fresh() {
        ClipPlayback.clear()
        items.clear()
        page = 0
        if (personal) Repo.resetClips()
        more()
    }
}

object Feeds {
    val forYou = ClipList({ Repo.clips() }, personal = true)
    val following = ClipList({ Repo.followingClips() }, personal = true)
    var viewer by mutableStateOf<ClipList?>(null)
        private set

    /** Opens full-screen swiping at [start] in [list]; keeps paging with [paged] if it has more. */
    fun open(title: String, list: List<VideoRef>, start: Int, paged: Paged? = null) {
        var cursor = paged
        val loader: (suspend () -> List<VideoRef>)? = if (paged?.hasMore == true) {
            suspend {
                val next = cursor?.more()
                cursor = next
                next?.items?.mapNotNull { (it as? Entry.Video)?.v } ?: emptyList()
            }
        } else null
        val l = ClipList(loader)
        l.items.addAll(list)
        l.page = start
        viewer = l
        Nav.go(Screen.Viewer(title, System.nanoTime()))
    }

    /** Buffer the first For You clips in the background so the app opens straight into a playing clip. */
    suspend fun warmUp() {
        // Only warm up as you: never load a signed-out feed that you'd then have to refresh away.
        if (!com.bitdroid.clips.data.Account.signedIn) return
        if (forYou.stale) forYou.fresh() else if (forYou.items.isEmpty()) forYou.more()
        forYou.items.getOrNull(0)?.let(ClipPlayback::preload)
        forYou.items.getOrNull(1)?.let(ClipPlayback::preload)
    }
}

/** Comments get fetched as soon as a clip is on screen, so the count shows and the sheet opens instantly. */
object CommentCache {
    val pages = mutableStateMapOf<String, Repo.CommentPage>()
    private val loading = mutableSetOf<String>()

    suspend fun prefetch(url: String) {
        if (pages.containsKey(url) || !loading.add(url)) return
        runCatching { Repo.comments(url) }.onSuccess { pages[url] = it }
        loading.remove(url)
    }
}

// ================================================================== the pager

@Composable
fun ClipPager(list: ClipList, modifier: Modifier = Modifier, topInset: Dp = 0.dp) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(list, com.bitdroid.clips.data.Account.session) { if (list.stale) list.fresh() else if (list.items.isEmpty()) list.more() }
    DisposableEffect(list) { onDispose { ClipPlayback.pause() } }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        val items = list.items
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().backdrop(), contentAlignment = Alignment.Center) {
                when {
                    list.loading -> CookieLoader(size = 64.dp)
                    list.error != null -> ErrorState(list.error!!) { scope.launch { list.more() } }
                    else -> MessageState(Icons.Rounded.VideoLibrary, "No clips here yet", null, "Try again", { scope.launch { list.more() } })
                }
            }
            return@Box
        }
        val pager = rememberPagerState(initialPage = list.page.coerceIn(0, items.lastIndex)) { items.size }
        LaunchedEffect(pager) {
            snapshotFlow { pager.currentPage }.collect { p ->
                items.getOrNull(p)?.let(ClipPlayback::preload)
                items.getOrNull(p + 1)?.let(ClipPlayback::preload)
            }
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { p ->
                list.page = p
                val v = items.getOrNull(p) ?: return@collect
                ClipPlayback.activate(v, listOfNotNull(items.getOrNull(p + 1), items.getOrNull(p + 2)), items.getOrNull(p - 1))
                launch { CommentCache.prefetch(v.url) }
                if (p >= items.size - 5) list.more()
            }
        }
        VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { items[it].id }) { page ->
            ClipPage(items[page], active = page == pager.settledPage, topInset = topInset)
        }
    }
}

// ================================================================== double-tap hearts

private class Heart(val id: Long, val at: Offset, val tilt: Float)

@Composable
private fun HeartBurst(h: Heart, onDone: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val spark = remember { Animatable(0f) }
    LaunchedEffect(h.id) {
        launch { spark.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        scale.animateTo(1.25f, spring(dampingRatio = 0.42f, stiffness = 520f))
        scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
        launch { rise.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        fade.animateTo(0f, tween(520, delayMillis = 60))
        onDone()
    }
    val density = LocalDensity.current
    val half = with(density) { 55.dp.toPx() }
    val spread = with(density) { 70.dp.toPx() }
    Box(
        Modifier.offset { IntOffset((h.at.x - half).roundToInt(), (h.at.y - half - rise.value * 160.dp.toPx()).roundToInt()) }
            .size(110.dp).graphicsLayer { alpha = fade.value },
        contentAlignment = Alignment.Center,
    ) {
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0 + h.tilt)
            Box(
                Modifier.offset {
                    val d = spark.value * spread
                    IntOffset((kotlin.math.cos(a) * d).roundToInt(), (kotlin.math.sin(a) * d).roundToInt())
                }.size(12.dp)
                    .graphicsLayer { val s = (1f - spark.value).coerceAtLeast(0f); scaleX = s; scaleY = s }
                    .clip(if (i % 2 == 0) Shapes.Sunny else Shapes.Clover)
                    .background(if (i % 2 == 0) Bit.accent else Bit.onPink),
            )
        }
        Icon(Icons.Rounded.Favorite, null, tint = Bit.accent, modifier = Modifier.size(96.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = h.tilt })
    }
}

// ================================================================== one clip

private val shadowText = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 6f)
private fun overlay(size: Float, weight: FontWeight = FontWeight.Normal) = body(size, Color.White, weight).merge(TextStyle(shadow = shadowText))

/** A rail action: icon with a soft shadow and a count underneath, the way TikTok lays them out. */
@Composable
private fun RailAction(icon: ImageVector, label: String?, tint: Color = Color.White, pop: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    val s by animateFloatAsState(if (pop) 1f else 0.94f, spring(dampingRatio = 0.35f, stiffness = 500f), label = "pop")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.squish(scale = 0.85f) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onClick() }) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(38.dp).graphicsLayer { scaleX = s; scaleY = s; shadowElevation = 6f })
        if (label != null) Text(label, style = overlay(12.5f, FontWeight.SemiBold))
    }
}

@Composable
private fun ClipPage(v: VideoRef, active: Boolean, topInset: Dp) {
    val context = LocalContext.current
    val view = LocalView.current
    var showPlay by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val slot = ClipPlayback.slot(v.id)
    val info = slot?.info
    val details = info?.let { i -> VideoRef.from(i).let { it.copy(short = true, avatar = it.avatar ?: v.avatar) } } ?: v
    val hearts = remember { mutableStateListOf<Heart>() }
    val bottom = bottomRoom()

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(v.id, active, details) {
                detectTapGestures(
                    onTap = { if (active) { ClipPlayback.toggle(); showPlay = slot?.player?.playWhenReady == false } },
                    onDoubleTap = { o ->
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        hearts += Heart(System.nanoTime(), o, Random.nextInt(-22, 23).toFloat())
                        if (!Library.isLiked(details)) Sync.like(details)
                    },
                )
            },
    ) {
        // Full-bleed: tall clips fill the screen edge to edge; wide ones fit with the cover blurred behind.
        Img(v.thumb, Modifier.fillMaxSize())
        if (slot != null) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boxAspect = maxWidth / maxHeight
                val a = slot.aspect.coerceIn(0.3f, 3f)
                val (w, h) = when {
                    a > 1f -> maxWidth to maxWidth / a // landscape: fit
                    a < boxAspect -> maxWidth to maxWidth / a // taller than the screen: cover width
                    else -> maxHeight * a to maxHeight // cover height
                }
                if (a > 1f) Box(Modifier.fillMaxSize().background(Color.Black))
                Box(Modifier.requiredSize(w, h)) { key(slot.player) { VideoSurface(slot.player, Modifier.fillMaxSize()) } }
            }
            if (active && slot.loading) CookieLoader(Modifier.align(Alignment.Center), 58.dp)
            slot.error?.let { Text(it, style = overlay(14f), modifier = Modifier.align(Alignment.Center).padding(28.dp)) }
        }
        AnimatedVisibility(active && showPlay && slot?.playing == false, Modifier.align(Alignment.Center), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            MorphPlayButton(false, 84.dp, { ClipPlayback.toggle(); showPlay = false }, bg = Color.White.copy(alpha = 0.22f), fg = Color.White)
        }
        // Readability gradients
        Box(Modifier.fillMaxWidth().height(160.dp + topInset).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(320.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))))

        // ---------------- right rail
        val liked = Library.isLiked(details)
        val saved = Library.inPlaylist(Library.WATCH_LATER, details)
        val likes = info?.likeCount?.takeIf { it >= 0 }
        val comments = CommentCache.pages[v.url]
        val commentLabel = comments?.total?.let { compact(it.toLong()) } ?: comments?.let { if (it.next != null) "${it.items.size}+" else "${it.items.size}" } ?: "…"
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = bottom + 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ch = details.channelUrl?.let { ChannelRef(it, details.channel, details.avatar) }
            Box(Modifier.size(56.dp, 66.dp)) {
                Box(Modifier.size(54.dp).align(Alignment.TopCenter).squish { ch?.let { Nav.go(Screen.Channel(it.url, it.name)) } }.clip(Shapes.forKey(details.channelUrl ?: details.channel)).background(Color.White).padding(2.dp)) {
                    Avatar(details.avatar, details.channelUrl ?: details.channel, 50.dp)
                }
                if (ch != null) {
                    val subbed = Library.isSubscribed(ch.url)
                    Box(
                        Modifier.size(24.dp).align(Alignment.BottomCenter).squish { Sync.subscribe(ch) }.clip(Shapes.Sunny).background(if (subbed) Color.White else Bit.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (subbed) Icons.Rounded.Check else Icons.Rounded.Add, if (subbed) "Following" else "Follow", tint = if (subbed) Bit.accent else Bit.onAccent, modifier = Modifier.size(16.dp)) }
                }
            }
            RailAction(Icons.Rounded.Favorite, likes?.let { compact(it + if (liked) 1 else 0) } ?: "Like", if (liked) Bit.accent else Color.White, liked) { Sync.like(details) }
            RailAction(Icons.AutoMirrored.Rounded.Comment, commentLabel) { Sheets.comments = details }
            RailAction(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Save", if (saved) Bit.accent else Color.White, saved) { Sync.watchLater(details) }
            RailAction(Icons.Rounded.Reply, "Share", Color.White) { share(context, details) }
            // The spinning record, Bloom style: a scalloped cookie with the creator inside.
            val spin = rememberInfiniteTransition(label = "disc")
            val r by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "r")
            Box(
                Modifier.size(46.dp).graphicsLayer { rotationZ = if (active && slot?.playing == true) r else 0f }.clip(Shapes.Ripple)
                    .background(Brush.sweepGradient(listOf(Color(0xFF1A1A1F), Bit.soft, Color(0xFF1A1A1F)))),
                contentAlignment = Alignment.Center,
            ) { Avatar(details.avatar, details.channelUrl ?: details.channel, 26.dp) }
        }

        // ---------------- caption
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 86.dp, bottom = bottom + 16.dp)) {
            Text(
                "@" + details.channel.ifBlank { "creator" }.replace(" ", ""),
                style = overlay(16.5f, FontWeight.Bold),
                modifier = Modifier.squish(scale = 0.97f) { details.channelUrl?.let { Nav.go(Screen.Channel(it, details.channel)) } },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                details.title, style = overlay(14.5f), maxLines = if (expanded) 12 else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.squish(scale = 0.99f) { expanded = !expanded },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "original sound · ${details.channel}   ${details.viewsText ?: details.views.takeIf { it >= 0 }?.let { "${compact(it)} views" } ?: ""}",
                    style = overlay(13f), maxLines = 1, modifier = Modifier.width(220.dp).basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }
        if (active && slot != null) {
            WavyProgress(
                rememberProgress(slot.player), slot.playing, { slot.player.seekTo(it) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = bottom - 6.dp),
                color = Color.White,
            )
        }
        hearts.toList().forEach { h -> key(h.id) { HeartBurst(h) { hearts.remove(h) } } }
    }
}
