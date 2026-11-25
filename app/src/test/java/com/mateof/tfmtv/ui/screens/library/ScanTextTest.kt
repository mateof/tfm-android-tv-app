package com.mateof.tfmtv.ui.screens.library

import com.mateof.tfmtv.core.Format
import com.mateof.tfmtv.data.model.LibraryScanStateDto
import com.mateof.tfmtv.ui.screens.player.HeldSeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanTextTest {

    @Test fun `reading the channels before knowing what to identify`() {
        val s = LibraryScanStateDto(running = true, channelsTotal = 5, channelsScanned = 2, currentChannel = "Series")
        assertEquals("Leyendo los canales (2 de 5) · Series", scanText(s))
        assertNull(scanFraction(s))
    }

    @Test fun `identifying, with progress`() {
        val s = LibraryScanStateDto(running = true, channelsTotal = 5, channelsScanned = 5, filesToProcess = 40, filesProcessed = 10, filesNew = 3)
        assertEquals("Identificando 10 de 40 ficheros · 3 nuevos", scanText(s))
        assertEquals(0.25f, scanFraction(s)!!, 0.0001f)
    }

    @Test fun `an old server only reports channels`() =
        assertEquals("Leyendo los canales (1 de 2)", scanText(LibraryScanStateDto(running = true, channelsTotal = 2, channelsScanned = 1)))

    @Test fun `what is left, rounded up to the minute`() {
        assertEquals("Quedan 43 min", Format.remaining(positionMs = 0, durationMs = 42 * 60_000L + 1))
        assertEquals("Queda 1 min", Format.remaining(positionMs = 59_000, durationMs = 60_000))
        assertEquals("Quedan 1 h 52 min", Format.remaining(positionMs = 0, durationMs = 112 * 60_000L))
        assertNull(Format.remaining(positionMs = 10, durationMs = 0))
        assertNull(Format.remaining(positionMs = 60_000, durationMs = 60_000))
    }

    @Test fun `a press is ten seconds and holding speeds up with time, not repeats`() {
        val seek = HeldSeek()
        assertEquals(10_000L, seek.step(repeat = 0, now = 0))
        // Repeats every 100 ms during the first second and a half: 30 s per second held.
        assertEquals(3_000L, seek.step(repeat = 1, now = 100))
        // Two seconds in, 90 s per second held; a gap is never counted as more than 600 ms.
        assertEquals(54_000L, seek.step(repeat = 2, now = 2_000))
        // A new press starts again.
        assertEquals(10_000L, seek.step(repeat = 0, now = 9_000))
    }
}
