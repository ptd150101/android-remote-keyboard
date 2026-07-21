package com.datpt.remotekey

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var windowsProxySpinner: Spinner
    private lateinit var captureCheck: CheckBox
    private lateinit var accessibilityStatus: TextView
    private lateinit var connectionStatus: TextView
    private lateinit var lastKeyStatus: TextView

    private val windowsProxyLabels = arrayOf(
        "Caps Lock → Windows (khuyên dùng)",
        "Ctrl phải → Windows",
        "Menu → Windows",
        "Không dùng phím thay thế"
    )
    private val windowsProxyKeyCodes = intArrayOf(
        KeyEvent.KEYCODE_CAPS_LOCK,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_UNKNOWN
    )

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "RemoteKey"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Chuyển bàn phím Bluetooth/USB từ Android sang Windows qua Wi-Fi LAN."
            textSize = 16f
            setPadding(0, dp(6), 0, dp(18))
        })

        accessibilityStatus = statusText()
        connectionStatus = statusText()
        lastKeyStatus = statusText()
        root.addView(accessibilityStatus)
        root.addView(connectionStatus)
        root.addView(lastKeyStatus)

        root.addView(label("IP máy tính"))
        hostInput = EditText(this).apply {
            hint = "Ví dụ: 192.168.1.20"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString(Prefs.HOST, "") ?: "")
        }
        root.addView(hostInput, fullWidth())

        root.addView(label("Cổng"))
        portInput = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt(Prefs.PORT, Prefs.DEFAULT_PORT).toString())
        }
        root.addView(portInput, fullWidth())

        root.addView(label("Mã kết nối"))
        tokenInput = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setText(prefs.getString(Prefs.TOKEN, Prefs.DEFAULT_TOKEN) ?: Prefs.DEFAULT_TOKEN)
        }
        root.addView(tokenInput, fullWidth())

        root.addView(label("Phím thay thế cho Windows trên HyperOS"))
        windowsProxySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                windowsProxyLabels
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            val savedKey = prefs.getInt(
                Prefs.WINDOWS_PROXY_KEY,
                Prefs.DEFAULT_WINDOWS_PROXY_KEY
            )
            val savedIndex = windowsProxyKeyCodes.indexOf(savedKey).let { if (it >= 0) it else 0 }
            setSelection(savedIndex)
        }
        root.addView(windowsProxySpinner, fullWidth())

        root.addView(TextView(this).apply {
            text = "HyperOS giữ phím Windows/Meta cho Home và Recent apps. Khi chuyển phím đang bật, lựa chọn này sẽ được gửi sang PC như phím Windows; ví dụ Caps Lock + Tab = Windows + Tab."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(4))
        })

        val saveButton = Button(this).apply {
            text = "Lưu cấu hình"
            setOnClickListener { saveSettings(showToast = true) }
        }
        root.addView(saveButton, fullWidth(top = dp(12)))

        val accessibilityButton = Button(this).apply {
            text = "Mở cài đặt Trợ năng"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(accessibilityButton, fullWidth(top = dp(8)))

        captureCheck = CheckBox(this).apply {
            text = "Bật chuyển toàn bộ bàn phím vật lý sang PC"
            textSize = 16f
            isChecked = prefs.getBoolean(Prefs.CAPTURE_ENABLED, false)
            setPadding(0, dp(14), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                if (checked && !isAccessibilityEnabled()) {
                    isChecked = false
                    Toast.makeText(
                        this@MainActivity,
                        "Hãy bật dịch vụ RemoteKey trong phần Trợ năng trước.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return@setOnCheckedChangeListener
                }

                if (checked && !saveSettings(showToast = false)) {
                    isChecked = false
                    return@setOnCheckedChangeListener
                }

                prefs.edit().putBoolean(Prefs.CAPTURE_ENABLED, checked).apply()
            }
        }
        root.addView(captureCheck, fullWidth())

        root.addView(TextView(this).apply {
            text = "Thoát khẩn cấp: giữ Ctrl + Alt + Shift rồi bấm F12. Chế độ chuyển phím sẽ tự tắt và PC được lệnh nhả toàn bộ phím."
            textSize = 15f
            setPadding(0, dp(12), 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Cách dùng: chạy agent trên Windows → nhập IP và token → bật RemoteKey trong Trợ năng → bật chuyển bàn phím → mở Parsec/Steam Link/StarDesk."
            textSize = 15f
        })

        val scroll = ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun saveSettings(showToast: Boolean): Boolean {
        val host = hostInput.text.toString().trim()
        val port = portInput.text.toString().toIntOrNull()
        val token = tokenInput.text.toString().trim()

        if (host.isBlank()) {
            hostInput.error = "Cần nhập IP máy tính"
            return false
        }
        if (port == null || port !in 1..65535) {
            portInput.error = "Cổng không hợp lệ"
            return false
        }
        if (token.isBlank()) {
            tokenInput.error = "Mã kết nối không được để trống"
            return false
        }

        val proxyIndex = windowsProxySpinner.selectedItemPosition
            .coerceIn(windowsProxyKeyCodes.indices)

        getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(Prefs.HOST, host)
            .putInt(Prefs.PORT, port)
            .putString(Prefs.TOKEN, token)
            .putInt(Prefs.WINDOWS_PROXY_KEY, windowsProxyKeyCodes[proxyIndex])
            .apply()

        if (showToast) {
            Toast.makeText(this, "Đã lưu cấu hình", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun refreshStatus() {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val enabled = isAccessibilityEnabled()
        accessibilityStatus.text = "Trợ năng: ${if (enabled) "ĐÃ BẬT" else "CHƯA BẬT"}"
        connectionStatus.text = "Kết nối: ${prefs.getString(Prefs.CONNECTION_STATUS, "Chưa chạy")}"
        lastKeyStatus.text = "Phím gần nhất: ${prefs.getString(Prefs.LAST_KEY, "—")}"

        val savedCapture = prefs.getBoolean(Prefs.CAPTURE_ENABLED, false)
        if (captureCheck.isChecked != savedCapture) {
            captureCheck.setOnCheckedChangeListener(null)
            captureCheck.isChecked = savedCapture
            captureCheck.setOnCheckedChangeListener { _, checked ->
                if (checked && !isAccessibilityEnabled()) {
                    captureCheck.isChecked = false
                    Toast.makeText(this, "Hãy bật RemoteKey trong Trợ năng.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else if (!checked || saveSettings(showToast = false)) {
                    prefs.edit().putBoolean(Prefs.CAPTURE_ENABLED, checked).apply()
                } else {
                    captureCheck.isChecked = false
                }
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, RemoteKeyAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun statusText(): TextView = TextView(this).apply {
        textSize = 15f
        gravity = Gravity.START
        setPadding(0, 2, 0, 2)
    }

    private fun fullWidth(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = top }
}
