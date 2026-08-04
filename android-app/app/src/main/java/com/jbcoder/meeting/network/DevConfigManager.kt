package com.jbcoder.meeting.network

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.jbcoder.meeting.BuildConfig

@Singleton
class DevConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dev_config", Context.MODE_PRIVATE)

    fun getCustomIp(): String? {
        return prefs.getString("custom_backend_ip", null)
    }

    fun setCustomIp(ip: String) {
        prefs.edit().putString("custom_backend_ip", ip).apply()
    }

    fun getLiveKitUrl(): String {
        val customIp = getCustomIp()
        return if (!customIp.isNullOrBlank()) {
            "ws://$customIp:7880"
        } else {
            BuildConfig.LIVEKIT_URL
        }
    }
}
