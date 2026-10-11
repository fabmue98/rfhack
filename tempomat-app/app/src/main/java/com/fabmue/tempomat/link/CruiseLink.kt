package com.fabmue.tempomat.link

import com.fabmue.tempomat.protocol.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A transport to the ESP32 that delivers whole protocol lines. Implementations
 * reconnect on their own until [stop] is called.
 */
interface CruiseLink {
    val state: StateFlow<LinkState>
    val lines: SharedFlow<String>

    fun start(scope: CoroutineScope)
    fun stop()

    /** Queues one line for sending; returns false when not connected. */
    fun send(line: String): Boolean

    companion object {
        const val RECONNECT_DELAY_MS = 2_000L
    }
}
