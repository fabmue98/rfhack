package com.fabmue.tempomat.link

import com.fabmue.tempomat.protocol.CruiseState
import com.fabmue.tempomat.protocol.DeviceConfig
import com.fabmue.tempomat.protocol.LinkState
import com.fabmue.tempomat.protocol.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays back a scripted drive so the dashboard can be tried on the radio
 * without an ESP32. The UI marks this mode clearly as DEMO.
 */
class DemoLink : CruiseLink {
    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state = _state.asStateFlow()
    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val lines = _lines.asSharedFlow()

    private var job: Job? = null
    private var config = DeviceConfig.DEFAULT

    override fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            _state.value = LinkState.CONNECTED
            _lines.emit(Protocol.frame("HELLO", listOf("fw" to "demo", "proto" to Protocol.VERSION.toString())))
            var tick = 0
            while (isActive) {
                _lines.emit(statusAt(tick))
                tick = (tick + 1) % CYCLE_TICKS
                delay(TICK_MS)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _state.value = LinkState.DISCONNECTED
    }

    override fun send(line: String): Boolean {
        if (_state.value != LinkState.CONNECTED) return false
        val raw = Protocol.unframe(line) ?: return false
        val reply = when (raw.type) {
            "GET" -> listOf(configLine())
            "SET" -> {
                val proposed = DeviceConfig(
                    stepKmh = raw.fields["step"]?.toIntOrNull() ?: -1,
                    minKmh = raw.fields["min"]?.toIntOrNull() ?: -1,
                    maxKmh = raw.fields["max"]?.toIntOrNull() ?: -1,
                )
                val error = proposed.validationError()
                if (error == null) config = proposed
                listOf(
                    Protocol.frame("ACK", listOf("cmd" to "SET", "ok" to if (error == null) "1" else "0", "msg" to (error ?: "ok"))),
                    configLine(),
                )
            }
            else -> emptyList()
        }
        reply.forEach { _lines.tryEmit(it) }
        return true
    }

    private fun configLine() = Protocol.frame(
        "CFG",
        listOf("step" to "${config.stepKmh}", "min" to "${config.minKmh}", "max" to "${config.maxKmh}"),
    )

    companion object {
        private const val TICK_MS = 200L
        private const val CYCLE_TICKS = 300 // 60 s loop

        /** Scripted status line for one tick of the demo loop. */
        fun statusAt(tick: Int): String {
            val t = tick % CYCLE_TICKS
            val (state, set, speed) = when {
                t < 25 -> Triple(CruiseState.OFF, 0, 40f + t * 1.6f)
                t < 50 -> Triple(CruiseState.STANDBY, 0, 80f + (t - 25) * 0.4f)
                t < 150 -> Triple(CruiseState.ACTIVE, 90, 90f + wobble(t))
                t < 170 -> Triple(CruiseState.OVERRIDE, 90, 90f + (t - 150) * 0.5f)
                t < 230 -> Triple(CruiseState.ACTIVE, 100, 100f + wobble(t))
                t < 240 -> Triple(CruiseState.STANDBY, 100, 100f - (t - 230) * 2f)
                else -> Triple(CruiseState.OFF, 0, 80f - (t - 240) * 0.5f)
            }
            val brake = t in 230 until 240
            val fields = mutableListOf(
                "st" to state.name,
                "set" to "$set",
                "v" to "%.1f".format(java.util.Locale.ROOT, speed),
                "clu" to "0",
                "brk" to if (brake) "1" else "0",
                "err" to "0",
            )
            if (brake) fields += "why" to "BRAKE"
            return Protocol.frame("STAT", fields)
        }

        private fun wobble(t: Int): Float = ((t % 10) - 5) * 0.08f
    }
}
