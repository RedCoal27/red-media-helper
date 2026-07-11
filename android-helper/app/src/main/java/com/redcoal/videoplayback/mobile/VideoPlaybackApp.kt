package com.redcoal.videoplayback.mobile

import android.app.Application
import com.redcoal.videoplayback.mobile.data.AppDatabase
import com.redcoal.videoplayback.mobile.download.DownloadRepository

class VideoPlaybackApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val downloads by lazy { DownloadRepository(this, database.downloadDao()) }
}
