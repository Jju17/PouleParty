package dev.rahier.pouleparty.ui.gamelogic

import com.google.firebase.Timestamp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.data.ApiErrorCode
import dev.rahier.pouleparty.data.ApiException
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Timing
import dev.rahier.pouleparty.model.ZoneCircle
import dev.rahier.pouleparty.ui.common.uiText
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class SharedMapLogicTest {

    private val start = Date(1_800_000_000_000L)
    private val circles = listOf(
        ZoneCircle(order = 0, radiusMeters = 1000.0, lat = 50.0, lng = 4.0),
        ZoneCircle(order = 1, radiusMeters = 800.5, lat = 50.001, lng = 4.001),
        ZoneCircle(order = 2, radiusMeters = 600.0, lat = 50.002, lng = 4.002),
    )
    private val game = Game(
        id = "g",
        gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
        timing = Timing(start = Timestamp(start), end = Timestamp(Date(start.time + 3_600_000))),
    ).let { it.copy(zone = it.zone.copy(shrinkIntervalMinutes = 5.0, radius = 1000.0)) }

    @Test
    fun `the active circle follows the stored schedule`() {
        val before = game.zoneRenderState(circles, Date(start.time - 60_000))
        assertEquals(1000, before.radius)
        val afterOneShrink = game.zoneRenderState(circles, Date(game.hunterStartDate.time + 5 * 60_000 + 1_000))
        assertEquals(800, afterOneShrink.radius)
        assertEquals(50.001, afterOneShrink.center!!.latitude(), 1e-9)
    }

    @Test
    fun `a launch that the server accepts reports no error`() = runTest {
        val functions = mockk<GameFunctions> { coEvery { launchGame("g") } returns Unit }
        assertNull(requestLaunch(functions, "g", "test"))
    }

    @Test
    fun `a refused launch reports the translated reason`() = runTest {
        val functions = mockk<GameFunctions> { coEvery { launchGame("g") } throws ApiException(ApiErrorCode.ALREADY_LAUNCHED) }
        assertEquals(uiText(R.string.api_error_already_launched), requestLaunch(functions, "g", "test"))
    }
}
