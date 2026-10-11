package com.fabmue.tempomat.link

import com.fabmue.tempomat.protocol.LineAssembler
import com.fabmue.tempomat.protocol.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * WLAN transport: the ESP32 runs a TCP server (default 192.168.4.1:3333 when it
 * is its own access point) and streams protocol lines.
 */
class TcpLink(private val host: String, private val port: Int) : CruiseLink {
    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state = _state.asStateFlow()
    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val lines = _lines.asSharedFlow()

    private val outgoing = Channel<String>(capacity = 16)
    private var job: Job? = null
    @Volatile private var socket: Socket? = null

    override fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                _state.value = LinkState.CONNECTING
                try {
                    Socket().use { socket ->
                        this@TcpLink.socket = socket
                        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                        socket.tcpNoDelay = true
                        _state.value = LinkState.CONNECTED
                        val writer = launch {
                            try {
                                val out = socket.getOutputStream()
                                for (line in outgoing) {
                                    out.write((line + "\n").toByteArray(Charsets.US_ASCII))
                                    out.flush()
                                }
                            } catch (_: IOException) {
                                // Closing the socket also ends the read loop.
                                runCatching { socket.close() }
                            }
                        }
                        try {
                            readLoop(socket)
                        } finally {
                            writer.cancel()
                        }
                    }
                } catch (_: IOException) {
                    // Fall through to reconnect.
                } finally {
                    socket = null
                }
                _state.value = LinkState.DISCONNECTED
                delay(CruiseLink.RECONNECT_DELAY_MS)
            }
        }
    }

    private suspend fun readLoop(socket: Socket) {
        val assembler = LineAssembler()
        val input = socket.getInputStream()
        val buf = ByteArray(512)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return
            for (line in assembler.feed(String(buf, 0, n, Charsets.US_ASCII))) _lines.emit(line)
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        // A blocking read ignores coroutine cancellation; closing the socket ends it.
        runCatching { socket?.close() }
        _state.value = LinkState.DISCONNECTED
    }

    override fun send(line: String): Boolean =
        _state.value == LinkState.CONNECTED && outgoing.trySend(line).isSuccess

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
    }
}
