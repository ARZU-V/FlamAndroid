package com.example.myflamcvgl

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.ConcurrentLinkedQueue


class WebServer {
    private val frameQueue = ConcurrentLinkedQueue<ByteArray>()
    private var server: CIOApplicationEngine? = null

    // MainActivity will call this to add a new processed frame
    fun sendFrame(frameData: ByteArray) {
        frameQueue.clear()
        frameQueue.offer(frameData)
    }

    fun start() {
        // Avoid starting if already running
        if (server != null) return

        server = embeddedServer(CIO, port = 8080) {
            install(WebSockets)
            routing {
                webSocket("/video") { // The endpoint our web client will connect to
                    try {
                        while (true) {
                            val frame = frameQueue.poll() // Get the latest frame
                            if (frame != null) {
                                send(Frame.Binary(true, frame))
                            }
                            // Small delay to prevent a tight loop from hogging the CPU
                            kotlinx.coroutines.delay(16)
                        }
                    } catch (e: ClosedReceiveChannelException) {
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

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        println("KTOR SERVER STOPPED")
    }
}