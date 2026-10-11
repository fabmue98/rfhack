package com.fabmue.tempomat

import com.fabmue.tempomat.protocol.CruiseState
import com.fabmue.tempomat.protocol.DeviceConfig
import com.fabmue.tempomat.protocol.Message
import com.fabmue.tempomat.protocol.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun frameAddsNmeaChecksum() {
        // XOR of "GET;what=cfg"
        val line = Protocol.requestConfig()
        assertTrue(line.startsWith("\$GET;what=cfg*"))
        val cs = line.substringAfter('*').toInt(16)
        assertEquals(Protocol.checksum("GET;what=cfg"), cs)
    }

    @Test
    fun parsesStatusWithChecksum() {
        val line = Protocol.frame(
            "STAT",
            listOf("st" to "ACTIVE", "set" to "90", "v" to "88.5", "clu" to "0", "brk" to "0", "err" to "0"),
        )
        val status = (Protocol.parse(line) as Message.Status).status
        assertEquals(CruiseState.ACTIVE, status.state)
        assertEquals(90, status.setSpeedKmh)
        assertEquals(88.5f, status.speedKmh!!, 0.01f)
        assertFalse(status.clutchPressed)
        assertFalse(status.brakePressed)
    }

    @Test
    fun acceptsLinesWithoutChecksumForSimpleTestSketches() {
        val status = (Protocol.parse("\$STAT;st=standby;set=0;v=50;brk=1;why=BRAKE\r") as Message.Status).status
        assertEquals(CruiseState.STANDBY, status.state)
        assertNull(status.setSpeedKmh)
        assertTrue(status.brakePressed)
        assertEquals("BRAKE", status.reason)
    }

    @Test
    fun rejectsWrongChecksum() {
        val good = Protocol.frame("STAT", listOf("st" to "ACTIVE", "set" to "90"))
        val bad = good.replace("set=90", "set=99")
        assertNull(Protocol.parse(bad))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Protocol.parse(""))
        assertNull(Protocol.parse("STAT;st=ACTIVE"))
        assertNull(Protocol.parse("\$*00"))
        assertNull(Protocol.parse("\$STAT;st=ACTIVE*ZZ"))
    }

    @Test
    fun unknownStateIsNotMistakenForActive() {
        val status = (Protocol.parse("\$STAT;st=WHATEVER;set=90") as Message.Status).status
        assertEquals(CruiseState.UNKNOWN, status.state)
    }

    @Test
    fun configRoundTrip() {
        val cfg = DeviceConfig(stepKmh = 5, minKmh = 40, maxKmh = 120)
        val sent = Protocol.unframe(Protocol.setConfig(cfg))!!
        assertEquals("SET", sent.type)
        val echoed = Protocol.frame("CFG", sent.fields.toList())
        assertEquals(Message.Config(cfg), Protocol.parse(echoed))
    }

    @Test
    fun incompleteConfigIsIgnored() {
        assertNull(Protocol.parse("\$CFG;step=5;min=40"))
    }

    @Test
    fun parsesAckAndHello() {
        assertEquals(Message.Ack("SET", false, "range"), Protocol.parse("\$ACK;cmd=SET;ok=0;msg=range"))
        assertEquals(Message.Hello("0.3", 1), Protocol.parse("\$HELLO;fw=0.3;proto=1"))
    }

    @Test
    fun configValidation() {
        assertNull(DeviceConfig.DEFAULT.validationError())
        assertEquals("step", DeviceConfig(3, 30, 130).validationError())
        assertEquals("range", DeviceConfig(1, 100, 100).validationError())
        assertEquals("max", DeviceConfig(1, 30, 200).validationError())
        assertEquals("min", DeviceConfig(1, 10, 100).validationError())
    }
}
