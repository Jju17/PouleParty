@file:OptIn(com.mapbox.maps.MapboxExperimental::class)

package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.zonePreviewColor
import dev.rahier.pouleparty.ui.components.zoomForRadius
import dev.rahier.pouleparty.ui.gamelogic.computeDebugShiftedCircles
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.ZoneGreen
import dev.rahier.pouleparty.ui.theme.gameboyStyle

/**
 * PP-13 phase 1 — read-only map preview of the upcoming game. Shows
 * the initial zone circle plus every future shrunk circle (rainbow
 * palette, numbered 1..N so the chicken reads the trajectory in
 * order) and the user-placed start + final pins inside the disc.
 * Hosts the PP-14 Shuffle button which regenerates
 * `Game.zone.driftSeed` (and re-picks the initial center). Phase 2
 * swaps the client-side `computeZoneRadius` +
 * `computeDebugShiftedCircles` calls for a PP-69 Cloud Function
 * response.
 */
@Composable
fun ZonesRecapStep(
    game: Game,
    onEntered: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onEntered() }

    val isStay = game.gameModEnum == GameMod.STAY_IN_THE_ZONE
    val previewCircles = remember(
        game.zone.driftSeed,
        game.zone.radius,
        game.zone.center,
        game.zone.finalCenter,
        game.timing.start,
        game.timing.end,
        game.timing.headStartMinutes,
        game.zone.shrinkIntervalMinutes,
        game.zone.shrinkMetersPerUpdate,
    ) {
        if (isStay) computeDebugShiftedCircles(game) else emptyList()
    }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(game.initialLocation)
            zoom(zoomForRadius(game.zone.radius * 1.15, game.initialLocation.latitude()).toDouble())
        }
    }
    // Re-fit the camera whenever the computed disc center or radius
    // shifts (entering the recap and shuffling both move them).
    LaunchedEffect(
        game.initialLocation.latitude(),
        game.initialLocation.longitude(),
        game.zone.radius,
    ) {
        mapViewportState.flyTo(
            CameraOptions.Builder()
                .center(game.initialLocation)
                .zoom(zoomForRadius(game.zone.radius * 1.15, game.initialLocation.latitude()).toDouble())
                .build()
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
        ) {
            // Initial radius — neon orange glow (matches PP-11 / PP-12).
            val initialPoints = circlePolygonPoints(game.initialLocation, game.zone.radius)
            PolylineAnnotation(points = initialPoints + listOf(initialPoints.first())) {
                lineColor = CROrange.copy(alpha = 0.15f)
                lineWidth = 8.0
            }
            PolylineAnnotation(points = initialPoints + listOf(initialPoints.first())) {
                lineColor = CROrange.copy(alpha = 0.35f)
                lineWidth = 4.0
            }
            PolylineAnnotation(points = initialPoints + listOf(initialPoints.first())) {
                lineColor = CROrange.copy(alpha = 0.9f)
                lineWidth = 2.0
            }

            // Future shrink circles (stayInTheZone only).
            previewCircles.forEachIndexed { index, circle ->
                val color = zonePreviewColor(index, previewCircles.size)
                val pts = circlePolygonPoints(circle.center, circle.radius)
                PolylineAnnotation(points = pts + listOf(pts.first())) {
                    lineColor = color.copy(alpha = 0.95f)
                    lineWidth = 2.5
                }
            }

            // Final disc glow.
            if (isStay) {
                game.finalLocation?.let { finalPos ->
                    val finalPts = circlePolygonPoints(finalPos, 50.0)
                    PolylineAnnotation(points = finalPts + listOf(finalPts.first())) {
                        lineColor = ZoneGreen.copy(alpha = 0.15f)
                        lineWidth = 8.0
                    }
                    PolylineAnnotation(points = finalPts + listOf(finalPts.first())) {
                        lineColor = ZoneGreen.copy(alpha = 0.5f)
                        lineWidth = 3.0
                    }
                    PolylineAnnotation(points = finalPts + listOf(finalPts.first())) {
                        lineColor = ZoneGreen.copy(alpha = 0.9f)
                        lineWidth = 1.5
                    }
                }
            }

            // PP-13: numbered badge on the initial disc (1) and on
            // every shrink circle (2..N) so the chicken reads the
            // trajectory in order. North-edge anchor keeps them on
            // a single bearing for easy visual scanning.
            ViewAnnotation(
                options = viewAnnotationOptions {
                    geometry(badgeAnchor(game.initialLocation, game.zone.radius, displayIndex = 1))
                    allowOverlap(true)
                }
            ) {
                ShrinkOrderBadge(index = 1, color = CROrange)
            }
            if (isStay) {
                previewCircles.forEachIndexed { index, circle ->
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(badgeAnchor(circle.center, circle.radius, displayIndex = index + 2))
                            allowOverlap(true)
                        }
                    ) {
                        ShrinkOrderBadge(
                            index = index + 2,
                            color = zonePreviewColor(index, previewCircles.size),
                        )
                    }
                }
            }

            // User-placed pins — distinct from the geometric initial
            // center which PP-13 picks elsewhere.
            ViewAnnotation(
                options = viewAnnotationOptions {
                    geometry(game.startPinPoint)
                    allowOverlap(true)
                }
            ) {
                MapLabel(text = "START", background = CROrange, textColor = Color.White)
            }

            if (isStay) {
                game.finalLocation?.let { finalPos ->
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(finalPos)
                            allowOverlap(true)
                        }
                    ) {
                        MapLabel(text = "FINAL", background = ZoneGreen, textColor = Color.Black)
                    }
                }
            }
        }

        // Shuffle button — stayInTheZone only.
        if (isStay) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .shadow(4.dp, RoundedCornerShape(50.dp))
                    .clip(RoundedCornerShape(50.dp))
                    .background(CROrange)
                    .clickable { onShuffle() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle", style = gameboyStyle(12), color = Color.Black)
                }
            }
        }
    }
}

/**
 * PP-13 numbered badge attached to each shrink circle so the chicken
 * reads the shrink order at a glance. The fill colour matches the
 * circle's stroke palette so users can pair label and outline.
 */
@Composable
private fun ShrinkOrderBadge(index: Int, color: Color) {
    Box(
        modifier = Modifier
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White, CircleShape)
            .size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$index", style = gameboyStyle(9), color = Color.Black)
    }
}

@Composable
private fun MapLabel(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = gameboyStyle(7), color = textColor)
    }
}

/**
 * Places the numbered badge on the circle's outline at a stable
 * pseudo-random angle in the **NW quadrant** (270° → 360°, i.e.
 * west through north). Same circle index → same bearing on every
 * render — Shuffle doesn't move the badges, only the circles
 * themselves.
 */
private fun badgeAnchor(center: Point, radius: Double, displayIndex: Int): Point {
    val bearingDeg = badgeBearingDegrees(displayIndex)
    val bearingRad = bearingDeg * Math.PI / 180.0
    val metersPerDegLat = 111_111.0
    val cosLat = kotlin.math.cos(center.latitude() * Math.PI / 180.0)
    val metersPerDegLng = if (cosLat == 0.0) metersPerDegLat else 111_111.0 * cosLat
    val dLat = (radius * kotlin.math.cos(bearingRad)) / metersPerDegLat
    val dLng = (radius * kotlin.math.sin(bearingRad)) / metersPerDegLng
    return Point.fromLngLat(center.longitude() + dLng, center.latitude() + dLat)
}

/**
 * Pure-function bearing in `[270°, 360°)` (north-west quadrant)
 * derived from `displayIndex` alone via splitmix64.
 */
private fun badgeBearingDegrees(displayIndex: Int): Double {
    var state = displayIndex.toLong() * -7046029254386353131L // 0x9E3779B97F4A7C15
    if (state == 0L) state = 1L
    state += -7046029254386353131L
    var z = state
    z = (z xor (z ushr 30)) * -4658895280553007687L // 0xBF58476D1CE4E5B9
    z = (z xor (z ushr 27)) * -7723592293110705685L // 0x94D049BB133111EB
    z = z xor (z ushr 31)
    val r = (z.toULong().toDouble()) / ULong.MAX_VALUE.toDouble()
    return 270.0 + r * 90.0
}
