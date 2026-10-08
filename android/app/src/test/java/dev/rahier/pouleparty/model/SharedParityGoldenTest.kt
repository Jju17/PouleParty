package dev.rahier.pouleparty.model

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.powerups.logic.generatePowerUps
import dev.rahier.pouleparty.ui.gamelogic.deterministicDriftCenter
import dev.rahier.pouleparty.ui.gamelogic.interpolateZoneCenter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Replays `parity/golden.json`, generated from the server implementation, so
 * a drift on any platform fails a test instead of relying on copied tables.
 */
class SharedParityGoldenTest {
    private val golden = JSONObject(File("../../parity/golden.json").readText())
    private val tolerance = 1e-9

    private fun JSONObject.point(key: String): Point =
        getJSONObject(key).let { Point.fromLngLat(it.getDouble("longitude"), it.getDouble("latitude")) }

    private fun JSONObject.optPoint(key: String): Point? = if (isNull(key)) null else point(key)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun assertPoint(expected: Point, actual: Point, label: String) {
        assertEquals("$label latitude", expected.latitude(), actual.latitude(), tolerance)
        assertEquals("$label longitude", expected.longitude(), actual.longitude(), tolerance)
    }

    @Test
    fun `distance matches the server haversine`() {
        golden.getJSONArray("distance").objects().forEach { v ->
            assertEquals(v.getDouble("meters"), distanceMeters(v.point("from"), v.point("to")), 1e-6)
        }
    }

    @Test
    fun `normal mode settings match`() {
        golden.getJSONArray("normalModeSettings").objects().forEach { v ->
            val (interval, decline) = calculateNormalModeSettings(v.getDouble("initialRadius"), v.getDouble("gameDurationMinutes"))
            assertEquals(v.getDouble("interval"), interval, tolerance)
            assertEquals(v.getDouble("decline"), decline, tolerance)
        }
    }

    @Test
    fun `interpolated centers match`() {
        golden.getJSONArray("interpolateZoneCenter").objects().forEach { v ->
            val actual = interpolateZoneCenter(
                v.point("initialCenter"), v.optPoint("finalCenter"), v.getDouble("initialRadius"), v.getDouble("currentRadius"),
            )
            assertPoint(v.point("result"), actual, "interpolate ${v.getDouble("currentRadius")}")
        }
    }

    @Test
    fun `drift centers match`() {
        golden.getJSONArray("deterministicDriftCenter").objects().forEach { v ->
            val actual = deterministicDriftCenter(
                basePoint = v.point("basePoint"),
                oldRadius = v.getDouble("oldRadius"),
                newRadius = v.getDouble("newRadius"),
                driftSeed = v.getLong("driftSeed").toInt(),
                finalCenter = v.optPoint("finalCenter"),
            )
            assertPoint(v.point("result"), actual, "drift seed ${v.getLong("driftSeed")}")
        }
    }

    @Test
    fun `spawned power-ups match`() {
        golden.getJSONArray("generatePowerUps").objects().forEach { v ->
            val types = v.getJSONArray("enabledTypes").let { a -> (0 until a.length()).map { a.getString(it) } }
            val actual = generatePowerUps(
                center = v.point("center"),
                radius = v.getDouble("radius"),
                count = v.getInt("count"),
                driftSeed = v.getLong("driftSeed").toInt(),
                batchIndex = v.getInt("batchIndex"),
                enabledTypes = types,
            )
            val expected = v.getJSONArray("result").objects()
            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEach { (e, a) ->
                assertEquals(e.getString("id"), a.id)
                assertEquals(e.getString("type"), a.type)
                assertEquals(e.getDouble("latitude"), a.location.latitude, tolerance)
                assertEquals(e.getDouble("longitude"), a.location.longitude, tolerance)
            }
        }
    }

    @Test
    fun `initial zone centers match`() {
        golden.getJSONArray("pickInitialZoneCenter").objects().forEach { v ->
            val actual = pickInitialZoneCenter(
                startPin = v.point("startPin"),
                finalCenter = v.point("finalCenter"),
                radius = v.getDouble("radius"),
                seed = v.getLong("seed").toInt(),
            )
            assertPoint(v.point("result"), actual, "pick seed ${v.getLong("seed")}")
        }
    }
}
