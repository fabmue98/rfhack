package com.fabmue.tempomat

import com.fabmue.tempomat.link.DemoLink
import com.fabmue.tempomat.protocol.CruiseState
import com.fabmue.tempomat.protocol.DisplayModel
import com.fabmue.tempomat.protocol.LinkState
import com.fabmue.tempomat.protocol.Message
import com.fabmue.tempomat.protocol.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayModelTest {
    private fun status(state: String) = Protocol.parse("\$STAT;st=$state;set=90;v=90")!!

    @Test
    fun freshStatusIsVisible() {
        val m = DisplayModel().withLink(LinkState.CONNECTED).reduce(status("ACTIVE"), nowMs = 1_000)
        assertEquals(CruiseState.ACTIVE, m.visibleStatus(1_500)?.state)
    }

    @Test
    fun staleStatusIsHidden() {
        val m = DisplayModel().withLink(LinkState.CONNECTED).reduce(status("ACTIVE"), nowMs = 1_000)
        assertNull(m.visibleStatus(1_000 + DisplayModel.STALE_AFTER_MS + 1))
    }

    @Test
    fun disconnectClearsStatus() {
        val m = DisplayModel().withLink(LinkState.CONNECTED).reduce(status("ACTIVE"), nowMs = 1_000)
            .withLink(LinkState.DISCONNECTED)
        assertNull(m.status)
        assertNull(m.visibleStatus(1_001))
    }

    @Test
    fun settingsOnlyEditableWhileOff() {
        val base = DisplayModel().withLink(LinkState.CONNECTED)
        assertTrue(base.reduce(status("OFF"), 0).canEditSettings(100))
        assertFalse(base.reduce(status("ACTIVE"), 0).canEditSettings(100))
        assertFalse(base.reduce(status("STANDBY"), 0).canEditSettings(100))
        assertFalse(base.reduce(status("OFF"), 0).canEditSettings(DisplayModel.STALE_AFTER_MS + 1))
        assertFalse(base.canEditSettings(0))
    }

    @Test
    fun protocolMismatchIsFlagged() {
        val m = DisplayModel().reduce(Message.Hello("x", Protocol.VERSION + 1), 0)
        assertTrue(m.protocolMismatch())
        assertFalse(DisplayModel().protocolMismatch())
    }

    @Test
    fun demoScriptAlwaysParses() {
        for (tick in 0 until 300) {
            assertNotNull("tick $tick", Protocol.parse(DemoLink.statusAt(tick)) as? Message.Status)
        }
    }
}
