package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagerDisguiseConfigTest {
    @Test
    fun roundTripsEnabledAndCustomCount() {
        for (enabled in listOf(false, true)) {
            for (count in listOf(2, 7, 99)) {
                val config = ManagerDisguiseConfig(enabled, count)
                assertEquals(config, ManagerDisguiseConfig.decode(config.encode() + "\n"))
            }
        }
    }

    @Test
    fun rejectsInvalidSavedConfiguration() {
        for (text in listOf("", "1", "2 7", "1 1", "0 100", "1 nope", "1 7 extra")) {
            assertNull(ManagerDisguiseConfig.decode(text))
        }
    }
}
