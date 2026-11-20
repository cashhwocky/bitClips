package com.bitdroid.clips

import android.app.Application
import com.bitdroid.clips.data.Library
import com.bitdroid.clips.data.Prefs
import com.bitdroid.clips.data.Repo
import com.bitdroid.clips.player.ClipPlayback

class BitClipsApp : Application() {
    /** For work that should outlive a screen (e.g. finishing sign-in). */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
        com.bitdroid.clips.data.Account.init(this)
        Repo.init()
        ClipPlayback.init(this)
        com.bitdroid.clips.data.Sync.init(this)
    }
}
