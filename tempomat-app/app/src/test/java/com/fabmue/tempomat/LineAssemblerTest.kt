package com.fabmue.tempomat

import com.fabmue.tempomat.protocol.LineAssembler
import org.junit.Assert.assertEquals
import org.junit.Test

class LineAssemblerTest {
    @Test
    fun joinsChunksSplitAcrossNotifications() {
        val a = LineAssembler()
        assertEquals(emptyList<String>(), a.feed("\$STAT;st=AC"))
        assertEquals(listOf("\$STAT;st=ACTIVE"), a.feed("TIVE\r\n\$CFG"))
        assertEquals(listOf("\$CFG;step=1"), a.feed(";step=1\n"))
    }

    @Test
    fun dropsOverlongLineAndRecovers() {
        val a = LineAssembler(maxLineLength = 8)
        assertEquals(emptyList<String>(), a.feed("0123456789abcdef"))
        assertEquals(listOf("ok"), a.feed("ghij\nok\n"))
    }

    @Test
    fun ignoresEmptyLines() {
        assertEquals(listOf("a", "b"), LineAssembler().feed("\n\r\na\n\nb\n"))
    }
}
