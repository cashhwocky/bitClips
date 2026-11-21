package com.bitdroid.clips.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.bitdroid.clips.data.Entry
import com.bitdroid.clips.data.Library
import com.bitdroid.clips.data.Prefs
import com.bitdroid.clips.data.Repo
import com.bitdroid.clips.data.VideoRef
import com.bitdroid.clips.data.toEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Small, quick buffers: start after half a second of video and keep ~25 s ahead. Several clips are
 * preloaded at once, so huge buffers on each would fight the one you're watching for bandwidth.
 */
private fun clipLoadControl() = androidx.media3.exoplayer.DefaultLoadControl.Builder()
    .setBufferDurationsMs(12_000, 25_000, 500, 1_500)
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()

private fun newPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context)
    .setLoadControl(clipLoadControl())
    .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
    .setHandleAudioBecomingNoisy(true)
    .setWakeMode(C.WAKE_MODE_NETWORK)
    .setSeekBackIncrementMs(10_000)
    .setSeekForwardIncrementMs(10_000)
    .build()

private fun friendly(e: Throwable): String {
    val m = e.message.orEmpty()
    return when {
        m.contains("age", true) && m.contains("restrict", true) -> "This video is age-restricted."
        m.contains("private", true) -> "This video is private."
        m.contains("sign in", true) || m.contains("bot", true) -> "YouTube asked to confirm you're not a bot. Try again in a bit."
        m.contains("unavailable", true) -> "This video isn't available."
        e is java.io.IOException -> "Couldn't connect. Are you online?"
        else -> "Couldn't play this video."
    }
}

/**
 * Clips play from a small pool of ready-to-go players: the clip on screen plus the next two (and the
 * one before) are already fetched, prepared and buffered, so swiping starts the next one instantly.
 */
object ClipPlayback {
    /** One preloaded clip. */
    class Slot(val id: String, val player: ExoPlayer) {
        var info by mutableStateOf<StreamInfo?>(null)
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        var aspect by mutableStateOf(9f / 16f)
        var playing by mutableStateOf(false)
        var job: Job? = null
        var counted = false
        /** Quality cap this clip loaded at, and the quality it actually plays. */
        var cap = 1080
        var quality by mutableIntStateOf(0)
        var stallJob: Job? = null
        var watchdog: Player.Listener? = null
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) loading = false }
            override fun onPlayerError(e: PlaybackException) { error = "Couldn't play this clip (${e.errorCodeName})"; loading = false }
        }
    }

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val slots = androidx.compose.runtime.mutableStateMapOf<String, Slot>()
    private val spare = ArrayDeque<ExoPlayer>()

    var activeId by mutableStateOf<String?>(null)
        private set
    val active: Slot? get() = activeId?.let { slots[it] }
    val playing: Boolean get() = active?.playing == true

    fun init(context: Context) { app = context.applicationContext }

    fun slot(id: String): Slot? = slots[id]

    /** Starts fetching + buffering [v] without playing it. Safe to call repeatedly. */
    fun preload(v: VideoRef) {
        if (slots.containsKey(v.id)) return
        val player = spare.removeFirstOrNull() ?: newPlayer(app)
        val slot = Slot(v.id, player)
        player.addListener(slot.listener)
        slot.watchdog = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                slot.stallJob?.cancel()
                // Stuck buffering in the middle of a clip: step down a quality and carry on from the same spot.
                if (state == Player.STATE_BUFFERING && player.playWhenReady && player.currentPosition > 0) {
                    slot.stallJob = scope.launch {
                        kotlinx.coroutines.delay(4000)
                        if (player.playbackState == Player.STATE_BUFFERING) stepDown(slot)
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                // Usually an expired or refused stream URL: fetch fresh links once and resume.
                scope.launch { recover(slot) }
            }
        }.also { player.addListener(it) }
        player.repeatMode = if (Prefs.loopClips) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.volume = 1f
        player.playWhenReady = false
        slots[v.id] = slot
        slot.job = scope.launch {
            try {
                val i = Repo.stream(v.url)
                slot.info = i
                // 720p is plenty for a phone-sized clip and starts fast.
                slot.cap = startCap()
                val (src, h) = Sources.build(i, slot.cap)
                slot.quality = h
                player.setMediaSource(src)
                player.prepare()
                if (activeId == v.id) player.play()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                slot.error = "Couldn't load this clip: ${e.message}"
                slot.loading = false
            }
        }
    }

    /** Shows [v]: plays its (already warm) player, pauses the rest, warms up [upcoming], drops the far ones. */
    fun activate(v: VideoRef, upcoming: List<VideoRef>, previous: VideoRef?) {
        activeId = v.id
        preload(v)
        upcoming.forEach(::preload)
        previous?.let(::preload)
        val keep = (listOf(v.id) + upcoming.map { it.id } + listOfNotNull(previous?.id)).toSet()
        slots.values.toList().forEach { s ->
            when {
                s.id == v.id -> {
                    if (s.player.playbackState == Player.STATE_ENDED) s.player.seekTo(0)
                    s.player.play()
                    if (!s.counted) {
                        s.counted = true
                        scope.launch {
                            // Count it once it has really started.
                            val i = s.info ?: run { while (s.info == null && s.error == null) kotlinx.coroutines.delay(200); s.info } ?: return@launch
                            VideoRef.from(i).copy(short = true).let { Library.addHistory(it); com.bitdroid.clips.data.Sync.watched(it) }
                        }
                    }
                }
                s.id in keep -> { s.player.pause(); if (s.player.currentPosition > 0) s.player.seekTo(0) }
                else -> release(s)
            }
        }
    }

    /** Best quality to start at: 1080p on Wi-Fi, 720p on mobile data. */
    private fun startCap(): Int {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        return if (cm?.isActiveNetworkMetered == true) 720 else 1080
    }

    private fun stepDown(slot: Slot) {
        val i = slot.info ?: return
        val lower = Sources.qualities(i).filter { it < slot.quality }.maxOrNull() ?: return
        slot.cap = lower
        val p = slot.player
        val pos = p.currentPosition
        runCatching {
            val (src, h) = Sources.build(i, lower)
            slot.quality = h
            p.setMediaSource(src, pos)
            p.prepare()
        }
    }

    private val recovering = mutableSetOf<String>()

    private suspend fun recover(slot: Slot) {
        if (!recovering.add(slot.id)) return
        val p = slot.player
        val pos = p.currentPosition
        runCatching {
            Repo.forget(slot.info?.url ?: return@runCatching)
            val i = Repo.stream(slot.info!!.url)
            slot.info = i
            val (src, h) = Sources.build(i, slot.cap)
            slot.quality = h
            slot.error = null
            p.setMediaSource(src, pos)
            p.prepare()
            if (activeId == slot.id) p.play()
        }
    }

    private fun release(s: Slot) {
        s.job?.cancel()
        s.stallJob?.cancel()
        recovering.remove(s.id)
        slots.remove(s.id)
        s.player.removeListener(s.listener)
        s.watchdog?.let { s.player.removeListener(it) }
        s.player.stop()
        s.player.clearMediaItems()
        if (spare.size < 2) spare.addLast(s.player) else s.player.release()
    }

    fun toggle() {
        val p = active?.player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() { slots.values.forEach { it.player.pause() } }

    /** Called when leaving Clips for good (e.g. a fresh batch): frees every player. */
    fun clear() {
        slots.values.toList().forEach(::release)
        activeId = null
    }
}
