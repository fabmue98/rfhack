package com.fabmue.tempomat.protocol

/**
 * Line protocol between the ESP32 cruise controller and this display app.
 *
 * Every message is one ASCII line in NMEA style:
 *
 *     $TYPE;key=value;key=value*CS\n
 *
 * `CS` is the XOR of all bytes between `$` and `*`, as two hex digits. The
 * checksum is always sent by the app; received lines without one are accepted
 * so a quick serial test sketch stays simple, but a wrong checksum drops the line.
 *
 * The ESP32 is the only side that controls the vehicle. The app only displays
 * status and edits comfort settings, which the ESP32 validates itself and only
 * accepts while cruise control is OFF. See tempomat-app/README.md.
 */
object Protocol {
    const val VERSION = 1

    fun checksum(body: String): Int = body.fold(0) { acc, c -> acc xor (c.code and 0xFF) }

    /** Builds a framed line (without trailing newline). */
    fun frame(type: String, fields: List<Pair<String, String>> = emptyList()): String {
        val body = buildString {
            append(type)
            for ((k, v) in fields) append(';').append(k).append('=').append(v)
        }
        return "$" + body + "*" + "%02X".format(checksum(body))
    }

    /** Splits a framed line into type and fields, or returns null if malformed. */
    fun unframe(raw: String): RawMessage? {
        val line = raw.trim()
        if (!line.startsWith("$") || line.length < 2) return null
        val star = line.lastIndexOf('*')
        val body: String
        if (star >= 0) {
            body = line.substring(1, star)
            val cs = line.substring(star + 1).toIntOrNull(16) ?: return null
            if (cs != checksum(body)) return null
        } else {
            body = line.substring(1)
        }
        val parts = body.split(';')
        val type = parts[0].trim().uppercase()
        if (type.isEmpty()) return null
        val fields = LinkedHashMap<String, String>()
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq <= 0) continue
            fields[p.substring(0, eq).trim().lowercase()] = p.substring(eq + 1).trim()
        }
        return RawMessage(type, fields)
    }

    fun parse(raw: String): Message? {
        val m = unframe(raw) ?: return null
        val f = m.fields
        return when (m.type) {
            "STAT" -> Message.Status(
                CruiseStatus(
                    state = CruiseState.fromWire(f["st"]),
                    setSpeedKmh = f["set"]?.toIntOrNull()?.takeIf { it > 0 },
                    speedKmh = f["v"]?.toFloatOrNull(),
                    clutchPressed = f["clu"].asFlag(),
                    brakePressed = f["brk"].asFlag(),
                    faultCode = f["err"]?.toIntOrNull() ?: 0,
                    reason = f["why"]?.takeIf { it.isNotEmpty() },
                )
            )
            "CFG" -> {
                val step = f["step"]?.toIntOrNull()
                val min = f["min"]?.toIntOrNull()
                val max = f["max"]?.toIntOrNull()
                if (step == null || min == null || max == null) null
                else Message.Config(DeviceConfig(step, min, max))
            }
            "ACK" -> Message.Ack(
                command = f["cmd"].orEmpty(),
                ok = f["ok"].asFlag(),
                text = f["msg"],
            )
            "HELLO" -> Message.Hello(
                firmware = f["fw"],
                protocolVersion = f["proto"]?.toIntOrNull(),
            )
            else -> Message.Unknown(m.type)
        }
    }

    /** Asks the ESP32 to report its current settings with a CFG line. */
    fun requestConfig(): String = frame("GET", listOf("what" to "cfg"))

    /** Proposes new settings. The ESP32 answers with ACK and, on success, CFG. */
    fun setConfig(cfg: DeviceConfig): String = frame(
        "SET",
        listOf(
            "step" to cfg.stepKmh.toString(),
            "min" to cfg.minKmh.toString(),
            "max" to cfg.maxKmh.toString(),
        )
    )

    private fun String?.asFlag(): Boolean = this == "1" || this.equals("true", ignoreCase = true)
}

data class RawMessage(val type: String, val fields: Map<String, String>)

sealed interface Message {
    data class Status(val status: CruiseStatus) : Message
    data class Config(val config: DeviceConfig) : Message
    data class Ack(val command: String, val ok: Boolean, val text: String?) : Message
    data class Hello(val firmware: String?, val protocolVersion: Int?) : Message
    data class Unknown(val type: String) : Message
}

enum class CruiseState {
    /** Cruise control switched off. */
    OFF,
    /** Armed, waiting for the driver to set a speed. */
    STANDBY,
    /** Holding the set speed. */
    ACTIVE,
    /** Driver is pressing the accelerator above the set speed. */
    OVERRIDE,
    /** ESP32 detected a fault and disengaged. */
    FAULT,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): CruiseState =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) && it != UNKNOWN }
                ?: UNKNOWN
    }
}

data class CruiseStatus(
    val state: CruiseState,
    val setSpeedKmh: Int?,
    val speedKmh: Float?,
    val clutchPressed: Boolean,
    val brakePressed: Boolean,
    val faultCode: Int,
    val reason: String?,
)

/** Comfort settings the ESP32 lets the driver change while cruise is OFF. */
data class DeviceConfig(val stepKmh: Int, val minKmh: Int, val maxKmh: Int) {
    /** Mirrors the limits the ESP32 must enforce itself; used to grey out bad input. */
    fun validationError(): String? = when {
        stepKmh !in ALLOWED_STEPS -> "step"
        minKmh < MIN_LIMIT || minKmh > MAX_LIMIT -> "min"
        maxKmh < MIN_LIMIT || maxKmh > MAX_LIMIT -> "max"
        minKmh >= maxKmh -> "range"
        else -> null
    }

    companion object {
        val ALLOWED_STEPS = listOf(1, 2, 5, 10)
        const val MIN_LIMIT = 30
        const val MAX_LIMIT = 140
        val DEFAULT = DeviceConfig(stepKmh = 1, minKmh = 30, maxKmh = 130)
    }
}
