package dev.rahier.pouleparty.ui.validation

import androidx.lifecycle.SavedStateHandle
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.data.ApiErrorCode
import dev.rahier.pouleparty.data.ApiException
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.Registration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ValidationQueueViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val gameRepository = mockk<GameRepository>()
    private val gameFunctions = mockk<GameFunctions>(relaxed = true)
    private val pending = MutableStateFlow(listOf(ChallengeSubmission(id = "s1", hunterId = "h1"), ChallengeSubmission(id = "s2", hunterId = "h2")))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { gameRepository.pendingSubmissionsFlow("g1") } returns pending
        every { gameRepository.challengesStream("g1") } returns emptyFlow()
        every { gameRepository.registrationsFlow("g1") } returns MutableStateFlow(listOf(Registration(userId = "h1", teamName = "Les Renards")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ValidationQueueViewModel(gameRepository, gameFunctions, SavedStateHandle(mapOf("gameId" to "g1")))

    @Test
    fun `pending proofs and team names are streamed`() {
        val vm = createViewModel()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("s1", "s2"), vm.uiState.value.submissions.map { it.id })
        assertEquals("Les Renards", vm.uiState.value.teamName("h1"))
        assertEquals("", vm.uiState.value.teamName("h2"))
    }

    @Test
    fun `accepting a proof calls the server and closes the detail`() {
        val vm = createViewModel()
        dispatcher.scheduler.runCurrent()
        val submission = vm.uiState.value.submissions.first()
        vm.onIntent(ValidationQueueIntent.SubmissionTapped(submission))
        vm.onIntent(ValidationQueueIntent.ValidateTapped(submission))
        dispatcher.scheduler.runCurrent()
        coVerify { gameFunctions.validateChallengeSubmission("g1", "s1", true) }
        assertNull(vm.uiState.value.selected)
        assertTrue(vm.uiState.value.busyIds.isEmpty())
    }

    @Test
    fun `a double tap sends one decision`() {
        val gate = CompletableDeferred<Unit>()
        coEvery { gameFunctions.validateChallengeSubmission(any(), any(), any()) } coAnswers { gate.await() }
        val vm = createViewModel()
        dispatcher.scheduler.runCurrent()
        val submission = vm.uiState.value.submissions.first()
        vm.onIntent(ValidationQueueIntent.RejectTapped(submission))
        dispatcher.scheduler.runCurrent()
        vm.onIntent(ValidationQueueIntent.RejectTapped(submission))
        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { gameFunctions.validateChallengeSubmission("g1", "s1", false) }
    }

    @Test
    fun `a refused decision shows the reason`() {
        coEvery { gameFunctions.validateChallengeSubmission(any(), any(), any()) } throws ApiException(ApiErrorCode.SUBMISSION_ALREADY_HANDLED)
        val vm = createViewModel()
        dispatcher.scheduler.runCurrent()
        vm.onIntent(ValidationQueueIntent.ValidateTapped(vm.uiState.value.submissions.first()))
        dispatcher.scheduler.runCurrent()
        assertEquals(R.string.api_error_submission_already_handled, vm.uiState.value.errorRes)
        vm.onIntent(ValidationQueueIntent.ErrorDismissed)
        assertNull(vm.uiState.value.errorRes)
    }

    @Test
    fun `the open proof closes when another referee handled it`() {
        val vm = createViewModel()
        dispatcher.scheduler.runCurrent()
        vm.onIntent(ValidationQueueIntent.SubmissionTapped(vm.uiState.value.submissions.first()))
        pending.value = listOf(ChallengeSubmission(id = "s2", hunterId = "h2"))
        dispatcher.scheduler.runCurrent()
        assertNull(vm.uiState.value.selected)
    }
}
