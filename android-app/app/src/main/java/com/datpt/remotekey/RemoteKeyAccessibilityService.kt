package com.datpt.remotekey

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class RemoteKeyAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var relay: RelayConnection

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        relay = RelayConnection(prefs)
        relay.start()
        updateStatus("Dịch vụ đã chạy")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        updateStatus("Dịch vụ bị gián đoạn")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::prefs.isInitialized || !prefs.getBoolean(Prefs.CAPTURE_ENABLED, false)) {
            return false
        }

        if (!isPhysicalKeyboardEvent(event)) {
            return false
        }

        if (isEmergencyChord(event)) {
            relay.releaseAll()
            prefs.edit().putBoolean(Prefs.CAPTURE_ENABLED, false).apply()
            updateLastKey("EMERGENCY: Ctrl+Alt+Shift+F12")
            return true
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP -> {
                val proxyKeyCode = prefs.getInt(
                    Prefs.WINDOWS_PROXY_KEY,
                    Prefs.DEFAULT_WINDOWS_PROXY_KEY
                )
                val relayedKeyCode = if (
                    proxyKeyCode != KeyEvent.KEYCODE_UNKNOWN && event.keyCode == proxyKeyCode
                ) {
                    KeyEvent.KEYCODE_META_LEFT
                } else {
                    event.keyCode
                }

                val originalName = KeyEvent.keyCodeToString(event.keyCode)
                val relayedName = KeyEvent.keyCodeToString(relayedKeyCode)
                val mapping = if (relayedKeyCode != event.keyCode) {
                    "$originalName → $relayedName"
                } else {
                    originalName
                }
                updateLastKey(
                    "$mapping ${if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"}"
                )
                relay.sendKey(event, relayedKeyCode)
                return true
            }
            KeyEvent.ACTION_MULTIPLE -> return true
        }

        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!::relay.isInitialized) return
        when (key) {
            Prefs.HOST,
            Prefs.PORT,
            Prefs.TOKEN,
            Prefs.CAPTURE_ENABLED -> relay.settingsChanged()
        }
    }

    override fun onDestroy() {
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        if (::relay.isInitialized) {
            relay.stop()
        }
        super.onDestroy()
    }

    private fun isPhysicalKeyboardEvent(event: KeyEvent): Boolean {
        val device = InputDevice.getDevice(event.deviceId)
        val keyboardSource = (event.source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
        return event.deviceId >= 0 && keyboardSource && (device == null || !device.isVirtual)
    }

    private fun isEmergencyChord(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.keyCode != KeyEvent.KEYCODE_F12) {
            return false
        }
        val meta = KeyEvent.normalizeMetaState(event.metaState)
        return (meta and KeyEvent.META_CTRL_ON) != 0 &&
            (meta and KeyEvent.META_ALT_ON) != 0 &&
            (meta and KeyEvent.META_SHIFT_ON) != 0
    }

    private fun updateStatus(value: String) {
        if (::prefs.isInitialized) {
            prefs.edit().putString(Prefs.CONNECTION_STATUS, value).apply()
        }
    }

    private fun updateLastKey(value: String) {
        prefs.edit().putString(Prefs.LAST_KEY, value).apply()
    }
}
