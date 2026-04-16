package dev.rahier.pouleparty.ui.gamelogic

import android.util.Log
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Snaps coordinates to the nearest walkable road using the Mapbox
 * Directions API. Each waypoint is independently snapped to the
 * nearest road segment. Falls back to the original position when
 * the API is unreachable or no snap is found.
 */
object RoadSnapService {

    private const val TAG = "RoadSnapService"
    private const val MAX_SNAP_DISTANCE_METERS = 200.0

    /**
     * Snaps a list of points to the nearest walkable roads.
     * Processes each point individually to guarantee independent snapping.
     */
    suspend fun snapToRoads(points: List<Point>, accessToken: String): List<Point> {
        if (points.isEmpty()) return points

        return coroutineScope {
            points.mapIndexed { _, point ->
                async { snapSinglePoint(point, accessToken) }
            }.awaitAll()
        }
    }

    /**
     * Snaps a single point to the nearest walkable road.
     * Uses the Directions API with a tiny offset point so the API
     * returns the snapped waypoint location.
     */
    private suspend fun snapSinglePoint(point: Point, accessToken: String): Point {
        return withContext(Dispatchers.IO) {
            try {
                // Create a second point ~10m north so the Directions API has a valid route
                val offsetLat = point.latitude() + 0.0001 // ~11m north
                val coordString = "${point.longitude()},${point.latitude()};${point.longitude()},$offsetLat"

                val urlString = "https://api.mapbox.com/directions/v5/mapbox/walking/$coordString" +
                    "?access_token=$accessToken" +
                    "&overview=false" +
                    "&steps=false"

                val connection = URL(urlString).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000

                    if (connection.responseCode != 200) {
                        Log.w(TAG, "Directions API returned ${connection.responseCode}")
                        return@withContext point
                    }

                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val waypoints = json.getJSONArray("waypoints")

                    if (waypoints.length() == 0) return@withContext point

                    val firstWaypoint = waypoints.getJSONObject(0)
                    val location = firstWaypoint.getJSONArray("location")
                    val snapped = Point.fromLngLat(location.getDouble(0), location.getDouble(1))

                    // Reject snaps that moved the point too far (> 200m)
                    val distance = haversineDistance(
                        point.latitude(), point.longitude(),
                        snapped.latitude(), snapped.longitude()
                    )
                    if (distance > MAX_SNAP_DISTANCE_METERS) {
                        Log.i(TAG, "Snap moved point > 200m, keeping original")
                        return@withContext point
                    }

                    snapped
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Road snap failed, using original position", e)
                point
            }
        }
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
