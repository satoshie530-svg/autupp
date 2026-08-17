package com.privatestore.tvmanager

import android.app.Application
import com.privatestore.tvmanager.util.NotificationHelper

class TvAppManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
