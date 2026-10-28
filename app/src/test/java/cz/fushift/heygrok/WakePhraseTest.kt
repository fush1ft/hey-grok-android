package cz.fushift.heygrok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WakePhraseTest {

    @Test
    fun fromKeyFallsBackToHeyGrok() {
        assertEquals(WakePhrase.HEY_GROK, WakePhrase.fromKey(null))
        assertEquals(WakePhrase.HEY_GROK, WakePhrase.fromKey("garbage"))
    }

    @Test
    fun fromKeyMatchesName() {
        assertEquals(WakePhrase.HEY_JARVIS, WakePhrase.fromKey(WakePhrase.HEY_JARVIS.name))
    }

    @Test
    fun thresholdKeysAreDistinct() {
        assertEquals(WakePhrase.entries.size, WakePhrase.entries.map { it.thresholdKey }.toSet().size)
    }

    @Test
    fun assetsExist() {
        for (phrase in WakePhrase.entries) {
            assertTrue(phrase.asset, File("src/main/assets", phrase.asset).exists())
        }
    }
}
