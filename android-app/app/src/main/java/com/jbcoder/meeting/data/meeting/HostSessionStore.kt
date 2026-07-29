package com.jbcoder.meeting.data.meeting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostSessionStore @Inject constructor() {

    private val _hostCredential = MutableStateFlow<String?>(null)
    val hostCredential: StateFlow<String?> = _hostCredential.asStateFlow()

    fun setHostCredential(credential: String?) {
        _hostCredential.value = credential
    }

    fun clear() {
        _hostCredential.value = null
    }
}
