package com.redcoal.redmedia.mobile

import android.app.Application
import com.redcoal.redmedia.mobile.data.AppDatabase
import com.redcoal.redmedia.mobile.download.DownloadRepository

class RedMediaApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val downloads by lazy { DownloadRepository(this, database.downloadDao()) }
}
