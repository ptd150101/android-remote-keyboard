package com.datpt.remotekey

import android.content.SharedPreferences
import android.os.Build
import android.view.KeyEvent
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RelayConnection(private val prefs: SharedPreferences) {
    private val running = AtomicBoolean(false)
    private val reconnectRequested = AtomicBoolean(false)
    private val needsReleaseAll = AtomicBoolean(true)
    private val queue = LinkedBlockingQueue<String>(2048)
    private val socketRef = AtomicReference<Socket?>(null)
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = Thread(::runLoop, "RemoteKey-TCP").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        releaseAll()
        closeSocket()
        worker?.interrupt()
    }

    fun settingsChanged() {
        queue.clear()
        needsReleaseAll.set(true)
        reconnectRequested.set(true)
        closeSocket()
        worker?.interrupt()
    }

    fun sendKey(event: KeyEvent, relayedKeyCode: Int = event.keyCode) {
        val message = JSONObject()
            .put("type", "key")
            .put("action", if (event.action == KeyEvent.ACTION_DOWN) "down" else "up")
            .put("keyCode", relayedKeyCode)
            .put("scanCode", event.scanCode)
            .put("metaState", event.metaState)
            .put("repeatCount", event.repeatCount)
            .put("deviceId", event.deviceId)
            .put("eventTime", event.eventTime)
            .toString()

        if (!queue.offer(message)) {
            queue.clear()
            needsReleaseAll.set(true)
            queue.offer(JSONObject().put("type", "release_all").toString())
            queue.offer(message)
            setStatus("Hàng đợi đầy; đang đồng bộ lại")
        }
    }

    fun releaseAll() {
        needsReleaseAll.set(true)
        queue.offer(JSONObject().put("type", "release_all").toString())
    }

    private fun runLoop() {
        while (running.get()) {
            if (!prefs.getBoolean(Prefs.CAPTURE_ENABLED, false)) {
                setStatus("Đang tắt")
                sleepQuietly(500)
                continue
            }

            val host = prefs.getString(Prefs.HOST, "")?.trim().orEmpty()
            val port = prefs.getInt(Prefs.PORT, Prefs.DEFAULT_PORT)
            val token = prefs.getString(Prefs.TOKEN, Prefs.DEFAULT_TOKEN).orEmpty()

            if (host.isBlank() || token.isBlank()) {
                setStatus("Thiếu IP hoặc mã kết nối")
                sleepQuietly(1000)
                continue
            }

            reconnectRequested.set(false)
            var socket: Socket? = null

            try {
                setStatus("Đang kết nối $host:$port")
                socket = Socket().apply {
                    keepAlive = true
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), 4000)
                    soTimeout = 5000
                }
                socketRef.set(socket)

                val writer = BufferedWriter(
                    OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                )
                val reader = BufferedReader(
                    InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                )

                val hello = JSONObject()
                    .put("type", "hello")
                    .put("token", token)
                    .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    .put("protocol", 1)
                    .toString()
                writeLine(writer, hello)

                val responseLine = reader.readLine() ?: error("Agent đóng kết nối")
                val response = JSONObject(responseLine)
                if (!response.optBoolean("ok", false)) {
                    error(response.optString("error", "Agent từ chối kết nối"))
                }

                socket.soTimeout = 0
                setStatus("Đã kết nối $host:$port")

                if (needsReleaseAll.getAndSet(false)) {
                    writeLine(writer, JSONObject().put("type", "release_all").toString())
                }

                while (running.get() &&
                    prefs.getBoolean(Prefs.CAPTURE_ENABLED, false) &&
                    !reconnectRequested.get()
                ) {
                    val message = queue.poll(5, TimeUnit.SECONDS)
                    if (message == null) {
                        writeLine(writer, JSONObject().put("type", "ping").toString())
                    } else {
                        writeLine(writer, message)
                        if (JSONObject(message).optString("type") == "release_all") {
                            needsReleaseAll.set(false)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                needsReleaseAll.set(true)
                if (running.get() && prefs.getBoolean(Prefs.CAPTURE_ENABLED, false)) {
                    setStatus("Mất kết nối: ${e.message ?: e.javaClass.simpleName}")
                }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                socketRef.compareAndSet(socket, null)
            }

            sleepQuietly(800)
        }
    }

    private fun writeLine(writer: BufferedWriter, message: String) {
        writer.write(message)
        writer.newLine()
        writer.flush()
    }

    private fun closeSocket() {
        try {
            socketRef.getAndSet(null)?.close()
        } catch (_: Exception) {
        }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            // A settings change intentionally wakes the loop.
        }
    }

    private fun setStatus(value: String) {
        prefs.edit().putString(Prefs.CONNECTION_STATUS, value).apply()
    }
}
