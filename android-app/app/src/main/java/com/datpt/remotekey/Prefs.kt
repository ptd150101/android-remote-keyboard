package com.datpt.remotekey

import android.view.KeyEvent

object Prefs {
    const val FILE = "remote_key_settings"
    const val HOST = "host"
    const val PORT = "port"
    const val TOKEN = "token"
    const val CAPTURE_ENABLED = "capture_enabled"
    const val CONNECTION_STATUS = "connection_status"
    const val LAST_KEY = "last_key"
    const val WINDOWS_PROXY_KEY = "windows_proxy_key"

    const val DEFAULT_PORT = 45892
    const val DEFAULT_TOKEN = "remotekey-123456"

    // HyperOS reserves the physical Meta/Windows key for Android shortcuts.
    // Caps Lock is used as a safe default proxy while capture mode is enabled.
    const val DEFAULT_WINDOWS_PROXY_KEY = KeyEvent.KEYCODE_CAPS_LOCK
}
