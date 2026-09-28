package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Test

class WearSyncProtocolTest {

    @Test
    fun startCommandCarriesGpsPreference() {
        assertEquals(
            "START|3|125|false",
            WearSyncProtocol.startCommand(
                planIndex = 3,
                elapsedSeconds = 125,
                gpsEnabled = false,
            ),
        )
    }

    @Test
    fun startCommandClampsNegativeIndexesAndElapsedTime() {
        assertEquals(
            "START|0|0|true",
            WearSyncProtocol.startCommand(
                planIndex = -5,
                elapsedSeconds = -9,
                gpsEnabled = true,
            ),
        )
    }
}
