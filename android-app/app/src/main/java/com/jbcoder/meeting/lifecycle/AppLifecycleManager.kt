package com.jbcoder.meeting.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jbcoder.meeting.feature.room.RoomSessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLifecycleManager @Inject constructor(
    private val roomSessionManager: RoomSessionManager
) : DefaultLifecycleObserver {

    private val _isForeground = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isForeground: kotlinx.coroutines.flow.StateFlow<Boolean> = _isForeground

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        _isForeground.value = true
        roomSessionManager.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        _isForeground.value = false
        roomSessionManager.onAppBackgrounded()
    }
}
