package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeCompletion
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.ui.challenges.ChallengesIntent
import dev.rahier.pouleparty.ui.challenges.ChallengesTab
import dev.rahier.pouleparty.ui.challenges.ChallengesUiState
import dev.rahier.pouleparty.ui.challenges.ChallengesViewModel
import dev.rahier.pouleparty.ui.challenges.LeaderboardHunterEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        every { repo.challengesStream() } returns emptyFlow()
        every { repo.challengeCompletionsStream(any()) } returns emptyFlow()
        coEvery { repo.getConfig(any()) } returns null
        coEvery { repo.fetchAllRegistrations(any()) } returns emptyList()
        coEvery { repo.fetchNicknames(any()) } returns emptyMap()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockUser(uid: String) {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        every { auth.currentUser } returns user
    }

    private fun create(
        gameId: String = "game-1",
        hunterId: String = "hunter-1"
    ): ChallengesViewModel {
        mockUser(hunterId)
        return ChallengesViewModel(
            firestoreRepository = repo,
            auth = auth,
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId))
        )
    }

    // ── Challenge loading ──────────────────────────────────

    @Test
    fun `challenges stream populates state`() {
        val streamed = listOf(
            Challenge(id = "c1", title = "Take a photo", body = "Selfie with a stranger", points = 10),
            Challenge(id = "c2", title = "Drink water", body = "Stay hydrated", points = 5)
        )
        every { repo.challengesStream() } returns flowOf(streamed)
        val vm = create()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.challenges.size)
        assertEquals("c1", vm.uiState.value.challenges.first().id)
    }

    @Test
    fun `completions stream populates state`() {
        val completions = listOf(
            ChallengeCompletion(
                hunterId = "h1",
                completedChallengeIds = listOf("c1"),
                totalPoints = 10,
                teamName = "Team Alpha"
            )
        )
        every { repo.challengeCompletionsStream("game-1") } returns flowOf(completions)
        val vm = create()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.completions.size)
        assertEquals("h1", vm.uiState.value.completions.first().hunterId)
    }

    // ── Tab selection ──────────────────────────────────────

    @Test
    fun `initial tab is Challenges`() {
        val vm = create()
        assertEquals(ChallengesTab.CHALLENGES, vm.uiState.value.selectedTab)
    }

    @Test
    fun `TabSelected Leaderboard updates state`() {
        val vm = create()
        vm.onIntent(ChallengesIntent.TabSelected(ChallengesTab.LEADERBOARD))
        assertEquals(ChallengesTab.LEADERBOARD, vm.uiState.value.selectedTab)
    }

    // ── Validate flow ──────────────────────────────────────

    @Test
    fun `ValidateTapped shows confirmation dialog state`() {
        val challenge = Challenge(id = "c1", title = "Hello", points = 5)
        every { repo.challengesStream() } returns flowOf(listOf(challenge))
        val vm = create()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ChallengesIntent.ValidateTapped(challenge))
        assertEquals(challenge, vm.uiState.value.pendingChallenge)
    }

    @Test
    fun `ValidateTapped on already-completed challenge is a no-op`() {
        val challenge = Challenge(id = "c1", title = "Hello", points = 5)
        every { repo.challengesStream() } returns flowOf(listOf(challenge))
        every { repo.challengeCompletionsStream("game-1") } returns flowOf(
            listOf(
                ChallengeCompletion(
                    hunterId = "hunter-1",
                    completedChallengeIds = listOf("c1"),
                    totalPoints = 5,
                    teamName = "Team"
                )
            )
        )
        val vm = create(hunterId = "hunter-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ChallengesIntent.ValidateTapped(challenge))
        assertNull(vm.uiState.value.pendingChallenge)
    }

    @Test
    fun `DismissConfirmation clears the pending challenge`() {
        val challenge = Challenge(id = "c1", title = "Hello", points = 5)
        val vm = create()
        vm.onIntent(ChallengesIntent.ValidateTapped(challenge))
        assertEquals(challenge, vm.uiState.value.pendingChallenge)
        vm.onIntent(ChallengesIntent.DismissConfirmation)
        assertNull(vm.uiState.value.pendingChallenge)
    }

    @Test
    fun `ConfirmValidation calls markChallengeCompleted with correct args`() {
        val challenge = Challenge(id = "c1", title = "Hello", points = 7)
        every { repo.challengesStream() } returns flowOf(listOf(challenge))
        coEvery { repo.getConfig("game-1") } returns Game(id = "game-1", hunterIds = listOf("hunter-1"))
        coEvery { repo.fetchAllRegistrations("game-1") } returns listOf(
            Registration(userId = "hunter-1", teamName = "Dream Team")
        )

        val vm = create(hunterId = "hunter-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ChallengesIntent.ValidateTapped(challenge))
        vm.onIntent(ChallengesIntent.ConfirmValidation)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            repo.markChallengeCompleted(
                gameId = "game-1",
                hunterId = "hunter-1",
                teamName = "Dream Team",
                challengeId = "c1",
                points = 7
            )
        }
        assertNull(vm.uiState.value.pendingChallenge)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `ConfirmValidation with no pending challenge is a no-op`() {
        val vm = create()
        vm.onIntent(ChallengesIntent.ConfirmValidation)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) {
            repo.markChallengeCompleted(any(), any(), any(), any(), any())
        }
    }

    // ── Leaderboard ────────────────────────────────────────

    @Test
    fun `leaderboard sorts by totalPoints descending and includes hunters with no completions`() {
        val completions = listOf(
            ChallengeCompletion(hunterId = "h1", totalPoints = 20, teamName = "Alpha"),
            ChallengeCompletion(hunterId = "h3", totalPoints = 50, teamName = "Gamma")
        )
        val state = ChallengesUiState(
            hunterIds = listOf("h1", "h2", "h3", "h4"),
            completions = completions,
            registrations = mapOf(
                "h1" to "Alpha",
                "h2" to "Beta",
                "h3" to "Gamma",
                "h4" to "Delta"
            ),
            currentHunterId = "h2"
        )
        val board = state.leaderboardEntries
        assertEquals(4, board.size)
        assertEquals("h3", board[0].hunterId)
        assertEquals(50, board[0].totalPoints)
        assertEquals("h1", board[1].hunterId)
        assertEquals(20, board[1].totalPoints)
        // The two zero-point hunters come next — tied, sorted by name ascending
        assertEquals(0, board[2].totalPoints)
        assertEquals(0, board[3].totalPoints)
        assertEquals("Beta", board[2].displayName)
        assertEquals("Delta", board[3].displayName)
    }

    @Test
    fun `leaderboard uses nickname fallback when no team registration`() {
        val state = ChallengesUiState(
            hunterIds = listOf("h1"),
            nicknames = mapOf("h1" to "PixelKing"),
            currentHunterId = "h1"
        )
        val board = state.leaderboardEntries
        assertEquals(1, board.size)
        assertEquals("PixelKing", board[0].displayName)
    }

    @Test
    fun `leaderboard falls back to Hunter when no team and no nickname`() {
        val state = ChallengesUiState(
            hunterIds = listOf("h1"),
            currentHunterId = "h1"
        )
        val board = state.leaderboardEntries
        assertEquals(1, board.size)
        assertEquals("Hunter", board[0].displayName)
    }

    @Test
    fun `current hunter entry is flagged as isCurrentHunter`() {
        val state = ChallengesUiState(
            hunterIds = listOf("h1", "h2"),
            registrations = mapOf("h1" to "Alpha", "h2" to "Beta"),
            currentHunterId = "h2"
        )
        val board = state.leaderboardEntries
        val current = board.firstOrNull { it.isCurrentHunter }
        assertNotNull(current)
        assertEquals("h2", current?.hunterId)
        // The other entry must not be flagged as current
        assertFalse(board.first { it.hunterId == "h1" }.isCurrentHunter)
    }

    @Test
    fun `completedIdsForCurrentHunter returns the current hunter's completions only`() {
        val completions = listOf(
            ChallengeCompletion(
                hunterId = "h1",
                completedChallengeIds = listOf("a", "b"),
                totalPoints = 15,
                teamName = "Team"
            ),
            ChallengeCompletion(
                hunterId = "h2",
                completedChallengeIds = listOf("c"),
                totalPoints = 5,
                teamName = "Other"
            )
        )
        val state = ChallengesUiState(
            completions = completions,
            currentHunterId = "h1"
        )
        assertEquals(setOf("a", "b"), state.completedIdsForCurrentHunter)
    }
}
