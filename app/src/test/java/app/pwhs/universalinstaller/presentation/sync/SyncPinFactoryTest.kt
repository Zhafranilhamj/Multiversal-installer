package app.pwhs.universalinstaller.presentation.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPinFactoryTest {

    @Test
    fun `generated pin is six digits with no leading zero`() {
        repeat(200) {
            val pin = SyncPinFactory.generatePin()
            assertEquals(SyncPinFactory.PIN_LENGTH, pin.length)
            assertTrue("'$pin' must not have a leading zero", pin.matches(Regex("[1-9][0-9]{5}")))
        }
    }

    @Test
    fun `generated pins are not a constant`() {
        // Regression for the hard-coded "1234" default.
        val pins = (1..200).map { SyncPinFactory.generatePin() }.toSet()

        assertTrue("expected real entropy, got ${pins.size} distinct pins", pins.size > 150)
        assertNotEquals(setOf("1234"), pins)
        assertTrue(pins.none { it == "1234" || it == "123456" })
    }

    @Test
    fun `two consecutive pins differ`() {
        assertNotEquals(SyncPinFactory.generatePin(), SyncPinFactory.generatePin())
    }
}
