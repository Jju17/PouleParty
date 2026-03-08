package dev.rahier.pouleparty.ui

import android.util.Log
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Snaps coordinates to the nearest walkable road using the Mapbox
 * Map Matching API.  Keeps original position when the API is unreachable
 * or no match is found.
 */
object RoadSnapService {

    private const val TAG = "RoadSnapService"

    /**
     * Snaps a list of points to the nearest walkable roads.
     * Returns a list of the same size; un-matchable points keep their original position.
     */
    suspend fun snapToRoads(points: List<Point>, accessToken: String): List<Point> {
        if (points.isEmpty()) return points

        // Map Matching API requires at least 2 coordinates.
        if (points.size == 1) {
            val pair = snapToRoads(listOf(points[0], points[0]), accessToken)
            return listOf(pair[0])
        }

        return withContext(Dispatchers.IO) {
            try {
                val coordString = points.joinToString(";") { "${it.longitude()},${it.latitude()}" }
                val radiuses = points.joinToString(";") { "50" }

                val urlString = "https://api.mapbox.com/matching/v5/mapbox/walking/$coordString" +
                    "?access_token=$accessToken" +
                    "&radiuses=$radiuses" +
                    "&steps=false" +
                    "&overview=false" +
                    "&geometries=geojson"

                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000

                if (connection.responseCode != 200) {
                    Log.w(TAG, "Map Matching API returned ${connection.responseCode}")
                    return@withContext points
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tracepoints = json.getJSONArray("tracepoints")

                val result = points.toMutableList()
                for (i in 0 until tracepoints.length()) {
                    if (i >= result.size || tracepoints.isNull(i)) continue
                    val tp = tracepoints.getJSONObject(i)
                    val location = tp.getJSONArray("location")
                    result[i] = Point.fromLngLat(location.getDouble(0), location.getDouble(1))
                }
                result
            } catch (e: Exception) {
                Log.w(TAG, "Road snapping failed, using original positions", e)
                points
            }
        }
    }
}
