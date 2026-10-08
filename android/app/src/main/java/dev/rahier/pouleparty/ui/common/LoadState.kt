package dev.rahier.pouleparty.ui.common

import dev.rahier.pouleparty.data.ApiErrorCode
import dev.rahier.pouleparty.data.ApiException
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.ZoneCircle
import kotlinx.coroutines.CancellationException
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import androidx.compose.ui.res.stringResource

/** A screen whose data comes from the backend is loading, ready, or failed with a reason. */
sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    data class Failed(@param:StringRes val messageRes: Int) : LoadState
}

@Composable
fun LoadStateScreen(loadState: LoadState, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (loadState) {
            LoadState.Loading, LoadState.Ready -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.loading_game), style = MaterialTheme.typography.bodyLarge)
            }
            is LoadState.Failed -> {
                Text(
                    stringResource(R.string.load_failed_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(loadState.messageRes),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
    }
}

/** The game and its stored zone schedule, or why they could not be read. */
suspend fun loadGameWithSchedule(
    gameRepository: GameRepository,
    gameId: String,
): Result<Pair<Game, List<ZoneCircle>>> = try {
    val game = gameRepository.getConfig(gameId)
        ?: throw ApiException(ApiErrorCode.GAME_NOT_FOUND)
    Result.success(game to gameRepository.fetchZoneSchedule(gameId))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
