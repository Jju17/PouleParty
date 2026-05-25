@file:Suppress("DEPRECATION")

package dev.rahier.pouleparty.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.powerups.ui.PowerUpsMapOverlay
import dev.rahier.pouleparty.ui.components.CountdownView
import dev.rahier.pouleparty.ui.components.GMChickenMarker
import dev.rahier.pouleparty.ui.components.HunterMapMarker
import dev.rahier.pouleparty.ui.components.LeaderboardContent
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.outerBoundsPoints
import dev.rahier.pouleparty.ui.components.zoomForRadius
import dev.rahier.pouleparty.ui.theme.CRDarkBackground
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.CRPink
import dev.rahier.pouleparty.ui.theme.ChickenYellow
import dev.rahier.pouleparty.ui.theme.HunterRed
import dev.rahier.pouleparty.ui.theme.ZoneGreen
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle
import dev.rahier.pouleparty.ui.victory.buildLeaderboardEntries
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

private enum class DemoTab(val titleRes: Int) {
    CHICKEN_MAP(R.string.demo_tab_chicken_map),
    HUNTER_MAP(R.string.demo_tab_hunter_map),
    GAME_MASTER_MAP(R.string.demo_tab_game_master_map),
    VICTORY(R.string.demo_tab_victory),
}

@OptIn(MapboxExperimental::class)
@Composable
fun DemoModeScreen(onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { DemoTab.entries.size })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CROrange,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.demo_mode_title),
                        style = bangerStyle(22),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onExit,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.demo_mode_quit))
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = 8.dp,
            ) {
                DemoTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(stringResource(tab.titleRes), fontSize = 12.sp) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (DemoTab.entries[page]) {
                    DemoTab.CHICKEN_MAP -> DemoChickenMapContent()
                    DemoTab.HUNTER_MAP -> DemoHunterMapContent()
                    DemoTab.GAME_MASTER_MAP -> DemoGameMasterMapContent()
                    DemoTab.VICTORY -> DemoVictoryContent()
                }
            }
        }
    }
}

@Composable
private fun rememberNowTicker(): Date {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    return Date(now)
}

@OptIn(MapboxExperimental::class)
@Composable
private fun DemoChickenMapContent() {
    val game = remember { DemoMockData.activeGame() }
    val powerUps = remember { DemoMockData.powerUps() }
    val hunters = remember { DemoMockData.hunters }
    val nowDate = rememberNowTicker()

    val mapViewportState = rememberMapViewportState()
    val zoneCenter = DemoMockData.zoneCenterPoint
    val radius = game.zone.radius

    LaunchedEffect(zoneCenter, radius) {
        mapViewportState.flyTo(
            cameraOptions = CameraOptions.Builder()
                .center(zoneCenter)
                .zoom(zoomForRadius(radius, zoneCenter.latitude()))
                .build()
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
        ) {
            val circlePoints = circlePolygonPoints(zoneCenter, radius)
            PolygonAnnotation(points = listOf(outerBoundsPoints(zoneCenter), circlePoints)) {
                fillColor = Color(0f, 0f, 0f, 0.3f)
                fillOpacity = 1.0
            }
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = ZoneGreen.copy(alpha = 0.9f)
                lineWidth = 2.5
            }

            PowerUpsMapOverlay(powerUps = powerUps, onMarkerClick = {})

            hunters.forEach { hunter ->
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(hunter.point)
                        allowOverlap(true)
                        allowOverlapWithPuck(true)
                    }
                ) {
                    HunterMapMarker(displayName = hunter.teamName)
                }
            }
        }

        DemoMapTopBar(
            titleRes = R.string.you_are_chicken,
            subtitle = "Demo • ${game.gameModEnum.title}",
            gradientColors = listOf(ChickenYellow, CROrange),
        )

        DemoMapBottomBar(radius = radius.toInt(), game = game, nowDate = nowDate, isChicken = true)
    }
}

@OptIn(MapboxExperimental::class)
@Composable
private fun DemoHunterMapContent() {
    val game = remember { DemoMockData.activeGame() }
    val powerUps = remember { DemoMockData.powerUps() }
    val nowDate = rememberNowTicker()

    val mapViewportState = rememberMapViewportState()
    val zoneCenter = DemoMockData.zoneCenterPoint
    val radius = game.zone.radius

    LaunchedEffect(zoneCenter, radius) {
        mapViewportState.flyTo(
            cameraOptions = CameraOptions.Builder()
                .center(zoneCenter)
                .zoom(zoomForRadius(radius, zoneCenter.latitude()))
                .build()
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
        ) {
            val circlePoints = circlePolygonPoints(zoneCenter, radius)
            PolygonAnnotation(points = listOf(outerBoundsPoints(zoneCenter), circlePoints)) {
                fillColor = Color(0f, 0f, 0f, 0.3f)
                fillOpacity = 1.0
            }
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = ZoneGreen.copy(alpha = 0.9f)
                lineWidth = 2.5
            }

            PowerUpsMapOverlay(powerUps = powerUps, onMarkerClick = {})
        }

        DemoMapTopBar(
            titleRes = R.string.you_are_hunter,
            subtitle = "Demo • ${game.gameModEnum.title}",
            gradientColors = listOf(HunterRed, CRPink),
        )

        DemoMapBottomBar(radius = radius.toInt(), game = game, nowDate = nowDate, isChicken = false)
    }
}

@OptIn(MapboxExperimental::class)
@Composable
private fun DemoGameMasterMapContent() {
    val game = remember { DemoMockData.activeGame() }
    val powerUps = remember { DemoMockData.powerUps() }
    val hunters = remember { DemoMockData.hunters }
    val chickenPoint = DemoMockData.chickenPoint

    val mapViewportState = rememberMapViewportState()
    val zoneCenter = DemoMockData.zoneCenterPoint
    val radius = game.zone.radius

    LaunchedEffect(zoneCenter, radius) {
        mapViewportState.flyTo(
            cameraOptions = CameraOptions.Builder()
                .center(zoneCenter)
                .zoom(zoomForRadius(radius, zoneCenter.latitude()))
                .build()
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
        ) {
            val circlePoints = circlePolygonPoints(zoneCenter, radius)
            PolygonAnnotation(points = listOf(outerBoundsPoints(zoneCenter), circlePoints)) {
                fillColor = Color(0f, 0f, 0f, 0.3f)
                fillOpacity = 1.0
            }
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = ZoneGreen.copy(alpha = 0.9f)
                lineWidth = 2.5
            }

            ViewAnnotation(
                options = viewAnnotationOptions {
                    geometry(chickenPoint)
                    allowOverlap(true)
                    allowOverlapWithPuck(true)
                }
            ) {
                GMChickenMarker(isInvisible = false)
            }

            hunters.forEach { hunter ->
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(hunter.point)
                        allowOverlap(true)
                        allowOverlapWithPuck(true)
                    }
                ) {
                    HunterMapMarker(displayName = hunter.teamName)
                }
            }

            PowerUpsMapOverlay(powerUps = powerUps, onMarkerClick = {})
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CROrange,
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "GameMaster 🦅",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${hunters.size} hunters",
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun DemoVictoryContent() {
    val game = remember { DemoMockData.victoryGame() }
    val registrations = remember {
        DemoMockData.hunters.map { Registration(userId = it.id, teamName = it.teamName) }
    }
    val entries = remember(game, registrations) {
        buildLeaderboardEntries(game = game, registrations = registrations, currentUserId = "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(text = "🏆", fontSize = 60.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.game_results),
            style = gameboyStyle(16),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        LeaderboardContent(
            entries = entries,
            hunterStartMs = game.hunterStartDate.time,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            onReport = {},
        )
        Spacer(Modifier.height(16.dp).navigationBarsPadding())
    }
}

@Composable
private fun DemoMapTopBar(
    titleRes: Int,
    subtitle: String,
    gradientColors: List<Color>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(gradientColors))
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(40.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(titleRes), style = bangerStyle(20), color = Color.White)
            Text(subtitle, style = gameboyStyle(10), color = Color.White.copy(alpha = 0.8f))
        }
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun DemoMapBottomBar(
    radius: Int,
    game: Game,
    nowDate: Date,
    isChicken: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CRDarkBackground.copy(alpha = 0.85f))
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.radius_format, radius),
                    style = gameboyStyle(14),
                    color = Color.White,
                )
                CountdownView(
                    nowDate = nowDate,
                    nextUpdateDate = Date(nowDate.time + 60_000L),
                    chickenStartDate = game.startDate,
                    hunterStartDate = game.hunterStartDate,
                    endDate = game.endDate,
                    isChicken = isChicken,
                )
            }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = HunterRed),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.size(width = 70.dp, height = 40.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    stringResource(R.string.found),
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
