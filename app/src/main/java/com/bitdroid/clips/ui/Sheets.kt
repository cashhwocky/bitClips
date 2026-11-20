package com.bitdroid.clips.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitdroid.clips.data.Repo
import com.bitdroid.clips.data.VideoRef
import com.bitdroid.clips.data.compact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/** Which sheet is open. Hosted once at the root. */
object Sheets {
    var comments by mutableStateOf<VideoRef?>(null)
}

fun share(context: Context, v: VideoRef) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.url).putExtra(Intent.EXTRA_SUBJECT, v.title)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Bit.surface,
        contentColor = Bit.text,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 6.dp).width(44.dp).height(5.dp).clip(CircleShape).background(Bit.outline)) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) { content() }
    }
}

@Composable
fun SheetHost() {
    Sheets.comments?.let { v -> CommentsSheet(v) { Sheets.comments = null } }
}

// ================================================================== comments

/** Comments under a clip, TikTok-style: count up top, likes on the right, replies tucked under each one. */
@Composable
private fun CommentsSheet(v: VideoRef, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cached = CommentCache.pages[v.url]
    val items = remember(v.url) { mutableStateListOf<Repo.Comment>().apply { cached?.let { addAll(it.items) } } }
    var next by remember(v.url) { mutableStateOf(cached?.next) }
    var total by remember(v.url) { mutableStateOf(cached?.total) }
    var loading by remember(v.url) { mutableStateOf(cached == null) }
    var error by remember(v.url) { mutableStateOf<String?>(null) }
    val replies = remember(v.url) { mutableStateMapOf<String, List<Repo.Comment>>() }
    val replyNext = remember(v.url) { mutableStateMapOf<String, Page?>() }
    val liked = remember(v.url) { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(v.url) {
        if (cached != null) return@LaunchedEffect
        try {
            val p = Repo.comments(v.url)
            CommentCache.pages[v.url] = p
            items.addAll(p.items); next = p.next; total = p.total
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            loading = false
        }
    }
    val state = rememberLazyListState()
    LoadMoreWhenNearEnd(state) {
        val page = next ?: return@LoadMoreWhenNearEnd
        if (loading) return@LoadMoreWhenNearEnd
        loading = true
        runCatching { Repo.moreComments(v.url, page) }.onSuccess { p ->
            val seen = items.map { it.id }.toHashSet()
            items.addAll(p.items.filter { it.id !in seen }); next = p.next
        }
        loading = false
    }

    BloomSheet(onDismiss) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            val count = total?.let { "${compact(it.toLong())} comments" } ?: if (items.isEmpty()) "Comments" else "${items.size}${if (next != null) "+" else ""} comments"
            Text(count, style = body(15f, Bit.text, FontWeight.Bold), modifier = Modifier.align(Alignment.Center))
            CookieButton(Icons.Rounded.Close, "Close", onDismiss, size = 38.dp, shape = CircleShape, bg = Bit.high, modifier = Modifier.align(Alignment.CenterEnd))
        }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.72f), state = state, contentPadding = PaddingValues(vertical = 8.dp)) {
            when {
                items.isEmpty() && loading -> item { CenterLoader() }
                items.isEmpty() && error != null -> item { ErrorState(error!!) { onDismiss(); Sheets.comments = v } }
                items.isEmpty() -> item { MessageState(Icons.AutoMirrored.Rounded.Comment, "No comments yet", "Comments might be turned off for this clip.") }
            }
            items(items, key = { it.id }) { c ->
                CommentRow(c, liked[c.id] == true, { liked[c.id] = liked[c.id] != true })
                val shown = replies[c.id]
                if (shown != null) {
                    shown.forEach { r -> Box(Modifier.padding(start = 52.dp)) { CommentRow(r, liked[r.id] == true, { liked[r.id] = liked[r.id] != true }, small = true) } }
                }
                val more = if (shown == null) c.replies else replyNext[c.id]
                if (c.replyCount > 0 && more != null) {
                    val left = (c.replyCount - (shown?.size ?: 0)).coerceAtLeast(0)
                    Text(
                        if (shown == null) "View $left ${if (left == 1) "reply" else "replies"}" else "View more",
                        style = body(13f, Bit.accent, FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 66.dp, top = 2.dp, bottom = 8.dp).squish(scale = 0.95f) {
                            scope.launch {
                                runCatching { Repo.moreComments(v.url, more) }.onSuccess { p ->
                                    replies[c.id] = (shown ?: emptyList()) + p.items
                                    replyNext[c.id] = p.next
                                }
                            }
                        },
                    )
                }
            }
            if (items.isNotEmpty() && loading) item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CookieLoader(size = 28.dp) } }
        }
        Text(
            "Liking comments here is just for you. Posting comments isn't supported yet.",
            style = body(11.5f, Bit.dim), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CommentRow(c: Repo.Comment, liked: Boolean, onLike: () -> Unit, small: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Avatar(c.avatar, c.authorUrl ?: c.author, if (small) 30.dp else 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.pinned) {
                    Icon(Icons.Rounded.PushPin, "Pinned", tint = Bit.accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(c.author.removePrefix("@").let { "@$it" }, style = body(12.5f, Bit.dim, FontWeight.SemiBold))
            }
            Spacer(Modifier.height(2.dp))
            Text(c.text, style = body(14.5f, Bit.text))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.age.orEmpty(), style = body(12f, Bit.dim))
                if (c.hearted) {
                    Spacer(Modifier.width(10.dp))
                    Text("♥ by creator", style = body(12f, Bit.accent, FontWeight.SemiBold))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp).squish(scale = 0.85f, onClick = onLike)) {
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (liked) Bit.accent else Bit.dim, modifier = Modifier.size(20.dp))
            Text(c.likes, style = body(11.5f, Bit.dim))
        }
    }
}
