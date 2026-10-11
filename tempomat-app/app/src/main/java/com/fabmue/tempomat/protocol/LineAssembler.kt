package com.fabmue.tempomat.protocol

/**
 * Reassembles newline-terminated lines from arbitrary chunks, as delivered by
 * BLE notifications or TCP reads. Oversized garbage is discarded so a noisy
 * link cannot grow the buffer without bound.
 */
class LineAssembler(private val maxLineLength: Int = 256) {
    private val buffer = StringBuilder()
    private var discarding = false

    fun feed(chunk: String): List<String> {
        val out = mutableListOf<String>()
        for (c in chunk) {
            when (c) {
                '\n', '\r' -> {
                    if (!discarding && buffer.isNotEmpty()) out += buffer.toString()
                    buffer.setLength(0)
                    discarding = false
                }
                else -> {
                    if (discarding) continue
                    if (buffer.length >= maxLineLength) {
                        buffer.setLength(0)
                        discarding = true
                    } else {
                        buffer.append(c)
                    }
                }
            }
        }
        return out
    }

    fun reset() {
        buffer.setLength(0)
        discarding = false
    }
}
