package com.jbcoder.meeting

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import com.jbcoder.meeting.lifecycle.AppLifecycleManager
import javax.inject.Inject

@HiltAndroidApp
class MeetingApplication : Application() {
    @Inject
    lateinit var appLifecycleManager: AppLifecycleManager

    override fun onCreate() {
        super.onCreate()
        appLifecycleManager.start()
    }
}
