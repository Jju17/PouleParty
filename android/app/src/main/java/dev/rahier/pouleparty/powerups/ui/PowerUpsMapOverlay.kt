package dev.rahier.pouleparty.powerups.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.mapbox.maps.extension.compose.MapboxMapComposable
import dev.rahier.pouleparty.ui.common.rememberReducedMotion
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.powerups.selection.powerUpColor
import dev.rahier.pouleparty.ui.components.circlePolygonPoints

/** Power-up markers with their collection radius, drawn inside a `MapboxMap` content block. */
@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@Composable
@MapboxMapComposable
@OptIn(MapboxExperimental::class)
fun PowerUpsMapOverlay(
    powerUps: List<PowerUp>,
    onMarkerClick: (PowerUp) -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "powerup-pulse")
    val pulse = transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "powerup-pulse-alpha"
    )

    powerUps.forEach { powerUp ->
        key(powerUp.id) {
            val ring = remember(powerUp.locationPoint) {
                circlePolygonPoints(center = powerUp.locationPoint, radiusMeters = AppConstants.POWER_UP_COLLECTION_RADIUS_METERS)
            }
            val alpha = if (reducedMotion) 0.14f else pulse.value
            PolygonAnnotation(points = listOf(ring)) {
                fillColor = powerUpColor(powerUp.typeEnum).copy(alpha = alpha)
                fillOpacity = 1.0
            }
            ViewAnnotation(
                options = viewAnnotationOptions {
                    geometry(powerUp.locationPoint)
                    allowOverlap(true)
                    allowOverlapWithPuck(true)
                }
            ) {
                PowerUpMapMarker(
                    type = powerUp.typeEnum,
                    onClick = { onMarkerClick(powerUp) }
                )
            }
        }
    }
}
