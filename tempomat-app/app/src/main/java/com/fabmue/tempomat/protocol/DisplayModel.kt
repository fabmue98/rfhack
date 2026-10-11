package com.fabmue.tempomat.protocol

/** Connection state of the transport to the ESP32. */
enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED }

/** Everything the dashboard shows, derived from link state and received messages. */
data class DisplayModel(
    val link: LinkState = LinkState.DISCONNECTED,
    val status: CruiseStatus? = null,
    val lastStatusAtMs: Long? = null,
    val config: DeviceConfig? = null,
    val firmware: String? = null,
    val protocolVersion: Int? = null,
    val lastAck: Message.Ack? = null,
) {
    /**
     * Status older than [STALE_AFTER_MS] is shown as unknown rather than as the
     * last value, so a dropped link never leaves "ACTIVE 90" on screen.
     */
    fun isStale(nowMs: Long): Boolean =
        link != LinkState.CONNECTED || lastStatusAtMs == null || nowMs - lastStatusAtMs > STALE_AFTER_MS

    fun visibleStatus(nowMs: Long): CruiseStatus? = if (isStale(nowMs)) null else status

    /** Settings may only be sent while the ESP32 reports cruise OFF with fresh data. */
    fun canEditSettings(nowMs: Long): Boolean =
        visibleStatus(nowMs)?.state == CruiseState.OFF

    fun protocolMismatch(): Boolean = protocolVersion != null && protocolVersion != Protocol.VERSION

    fun reduce(message: Message, nowMs: Long): DisplayModel = when (message) {
        is Message.Status -> copy(status = message.status, lastStatusAtMs = nowMs)
        is Message.Config -> copy(config = message.config)
        is Message.Ack -> copy(lastAck = message)
        is Message.Hello -> copy(firmware = message.firmware, protocolVersion = message.protocolVersion)
        is Message.Unknown -> this
    }

    fun withLink(state: LinkState): DisplayModel =
        if (state == LinkState.CONNECTED) copy(link = state)
        else copy(link = state, status = null, lastStatusAtMs = null)

    companion object {
        const val STALE_AFTER_MS = 2_000L
    }
}
