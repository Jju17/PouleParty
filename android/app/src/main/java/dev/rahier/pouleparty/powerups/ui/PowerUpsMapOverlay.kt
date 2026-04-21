package dev.rahier.pouleparty.powerups.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.powerups.selection.powerUpColor
import dev.rahier.pouleparty.ui.components.circlePolygonPoints

/**
 * Power-up markers + collection-radius discs, shared between the chicken
 * and hunter maps. Each power-up renders:
 *   - A semi-transparent pulsing filled disc matching its collection radius
 *     (so players see exactly where auto-collection triggers).
 *   - A circular icon marker (`PowerUpMapMarker`) centered on the disc.
 *
 * Must be called within a `MapboxMap { ... }` scope.
 */
@Composable
@OptIn(MapboxExperimental::class)
fun PowerUpsMapOverlay(
    powerUps: List<PowerUp>,
    onMarkerClick: (PowerUp) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "powerup-pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "powerup-pulse-alpha"
    )

    powerUps.forEach { powerUp ->
        val circlePoints = circlePolygonPoints(
            center = powerUp.locationPoint,
            radiusMeters = AppConstants.POWER_UP_COLLECTION_RADIUS_METERS
        )
        PolygonAnnotation(points = listOf(circlePoints)) {
            fillColor = powerUpColor(powerUp.typeEnum).copy(alpha = pulseAlpha)
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
