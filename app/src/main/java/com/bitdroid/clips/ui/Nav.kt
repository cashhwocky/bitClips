package com.bitdroid.clips.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bitdroid.clips.data.Entry
import com.bitdroid.clips.data.Paged
import kotlinx.coroutines.CancellationException

sealed interface Screen {
    data object Home : Screen
    data object Discover : Screen
    data object Following : Screen
    data object Profile : Screen
    data object Settings : Screen
    data class Search(val query: String) : Screen
    data class Channel(val url: String, val name: String) : Screen
    /** Full-screen swiping through a grid you tapped into (search, a channel, your likes…). */
    data class Viewer(val title: String, val token: Long) : Screen
}

/** A simple back stack; the bottom of the stack is always one of the four tabs. */
object Nav {
    val tabs = listOf(Screen.Home, Screen.Discover, Screen.Following, Screen.Profile)
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top: Screen get() = stack.last()

    fun go(s: Screen) { if (stack.last() != s) stack.add(s) }

    fun tab(i: Int) {
        stack.clear()
        stack.add(tabs[i])
    }

    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        if (stack.last() != Screen.Home) { tab(0); return true }
        return false
    }
}

/** A list that loads page by page as you scroll. */
class PagedList(private val load: suspend () -> Paged) {
    val items = mutableStateListOf<Entry>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var last: Paged? = null
    val hasMore: Boolean get() = last?.hasMore == true

    suspend fun refresh() {
        loading = true
        error = null
        try {
            val p = load()
            items.clear()
            items.addAll(p.items.distinctBy { key(it) })
            last = p
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "${e.javaClass.simpleName}: ${e.message ?: "no details"}"
        } finally {
            loading = false
        }
    }

    suspend fun more() {
        val p = last ?: return
        if (loading || !p.hasMore) return
        loading = true
        try {
            val next = p.more() ?: return
            val seen = items.map { key(it) }.toHashSet()
            items.addAll(next.items.filter { key(it) !in seen })
            last = next
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        } finally {
            loading = false
        }
    }

    private fun key(e: Entry) = when (e) {
        is Entry.Video -> e.v.url
        is Entry.Channel -> e.c.url
    }
}

@Composable
fun rememberPaged(key: Any?, load: suspend () -> Paged): PagedList {
    val list = remember(key) { PagedList(load) }
    LaunchedEffect(key) { if (list.items.isEmpty()) list.refresh() }
    return list
}
