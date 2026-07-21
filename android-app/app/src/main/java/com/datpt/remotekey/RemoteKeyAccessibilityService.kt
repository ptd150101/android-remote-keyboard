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

    private val heldModifiers = linkedSetOf<Int>()
    private val relayedModifiers = mutableSetOf<Int>()
    private val activeComboKeys = mutableMapOf<Int, Int>()

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
        resetShortcutState(releaseRemote = true)
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
            resetShortcutState(releaseRemote = true)
            prefs.edit().putBoolean(Prefs.CAPTURE_ENABLED, false).apply()
            updateLastKey("EMERGENCY: Ctrl+Alt+Shift+F12")
            return true
        }

        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return false
        }

        if (isRelayModifier(event.keyCode)) {
            handleModifier(event)
            return true
        }

        val activeModifier = activeComboKeys[event.keyCode]
            ?: findSupportedModifier(event.keyCode)

        if (activeModifier != null) {
            handleSupportedCombo(event, activeModifier)
            return true
        }

        // Do not let an unsupported Alt/Windows chord degrade into an unmodified
        // key in the remote app after its modifier was consumed here.
        if (heldModifiers.isNotEmpty()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                updateLastKey("Bỏ qua tổ hợp: ${describeHeldModifiers()} + ${KeyEvent.keyCodeToString(event.keyCode)}")
            }
            return true
        }

        // Ordinary typing and non-system shortcuts stay on the remote app's
        // native input path. This avoids an extra TCP relay hop and duplicate keys.
        return false
    }

    private fun handleModifier(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> heldModifiers.add(event.keyCode)
            KeyEvent.ACTION_UP -> {
                heldModifiers.remove(event.keyCode)
                if (relayedModifiers.remove(event.keyCode)) {
                    relay.sendKey(event)
                }
            }
        }
    }

    private fun handleSupportedCombo(event: KeyEvent, modifierKeyCode: Int) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (relayedModifiers.add(modifierKeyCode)) {
                    relay.sendKey(copyKeyEvent(event, KeyEvent.ACTION_DOWN, modifierKeyCode))
                }
                activeComboKeys[event.keyCode] = modifierKeyCode
                relay.sendKey(event)
                updateLastKey(shortcutName(modifierKeyCode, event.keyCode))
            }

            KeyEvent.ACTION_UP -> {
                if (activeComboKeys.remove(event.keyCode) != null) {
                    relay.sendKey(event)
                }
            }
        }
    }

    private fun findSupportedModifier(keyCode: Int): Int? {
        val meta = heldModifiers.firstOrNull(::isMetaKey)
        if (meta != null && (keyCode == KeyEvent.KEYCODE_E || keyCode == KeyEvent.KEYCODE_TAB)) {
            return meta
        }

        val alt = heldModifiers.firstOrNull(::isAltKey)
        if (alt != null && keyCode == KeyEvent.KEYCODE_TAB) {
            return alt
        }

        return null
    }

    private fun shortcutName(modifierKeyCode: Int, keyCode: Int): String {
        val modifier = when {
            isMetaKey(modifierKeyCode) -> "Windows"
            isAltKey(modifierKeyCode) -> "Alt"
            else -> KeyEvent.keyCodeToString(modifierKeyCode)
        }
        val key = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        return "$modifier + $key"
    }

    private fun describeHeldModifiers(): String = heldModifiers.joinToString("+") {
        when {
            isMetaKey(it) -> "Windows"
            isAltKey(it) -> "Alt"
            else -> KeyEvent.keyCodeToString(it)
        }
    }

    private fun copyKeyEvent(source: KeyEvent, action: Int, keyCode: Int): KeyEvent = KeyEvent(
        source.downTime,
        source.eventTime,
        action,
        keyCode,
        0,
        source.metaState,
        source.deviceId,
        0,
        source.flags,
        source.source
    )

    private fun isRelayModifier(keyCode: Int): Boolean = isAltKey(keyCode) || isMetaKey(keyCode)

    private fun isAltKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT

    private fun isMetaKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!::relay.isInitialized) return
        when (key) {
            Prefs.HOST, Prefs.PORT, Prefs.TOKEN, Prefs.CAPTURE_ENABLED -> {
                if (key == Prefs.CAPTURE_ENABLED &&
                    sharedPreferences?.getBoolean(Prefs.CAPTURE_ENABLED, false) != true
                ) {
                    resetShortcutState(releaseRemote = true)
                }
                relay.settingsChanged()
            }
        }
    }

    override fun onDestroy() {
        resetShortcutState(releaseRemote = true)
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        if (::relay.isInitialized) {
            relay.stop()
        }
        super.onDestroy()
    }

    private fun resetShortcutState(releaseRemote: Boolean) {
        heldModifiers.clear()
        relayedModifiers.clear()
        activeComboKeys.clear()
        if (releaseRemote && ::relay.isInitialized) {
            relay.releaseAll()
        }
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
