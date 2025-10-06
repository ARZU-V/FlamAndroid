package com.example.myflamcvgl

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * This whole class is just a mini web server that runs on the phone.
 * Its only job is to send the video frames over WiFi.
 */
class WebServer {
    // A safe place to put the picture frames while we wait to send them.
    private val frameQueue = ConcurrentLinkedQueue<ByteArray>()
    private var server: CIOApplicationEngine? = null

    /**
     * MainActivity calls this to give us a new frame to send.
     */
    fun sendFrame(frameData: ByteArray) {
        // We clear old frames so we only send the very latest one. Keeps it live.
        frameQueue.clear()
        frameQueue.offer(frameData)
    }

    // Fires up the server so the web browser can connect.
    fun start() {
        // Avoid starting if already running
        if (server != null) return

        server = embeddedServer(CIO, port = 8080) {
            install(WebSockets)
            routing {
                // This is the address our website connects to, like ws://.../video
                webSocket("/video") {
                    try {
                        // This loop runs forever, constantly checking for new frames to send.
                        while (true) {
                            val frame = frameQueue.poll() // grab the latest frame from the queue.
                            if (frame != null) {
                                send(Frame.Binary(true, frame)) // send it!
                            }
                            // A little pause so we don't cook the CPU, aims for ~60fps.
                            kotlinx.coroutines.delay(16)
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        // This just means the browser closed the page, which is normal.
                        println("WebSocket connection closed.")
                    } catch (e: Exception) {
                        println("Error in WebSocket: ${e.message}")
                    }
                }
            }
        }
        server?.start(wait = false)
        println("KTOR SERVER STARTED")
    }

    // Shuts down the server cleanly when the app is paused or closed.
    fun stop() {
        server?.stop(1000, 2000)
        server = null
        println("KTOR SERVER STOPPED")
    }
}