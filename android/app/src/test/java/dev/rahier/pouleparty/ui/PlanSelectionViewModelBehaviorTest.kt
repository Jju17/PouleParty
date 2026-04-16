package dev.rahier.pouleparty.ui

import com.google.firebase.auth.FirebaseAuth
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.PartyPlansConfig
import dev.rahier.pouleparty.model.PartyPlansConfig.DepositPlan
import dev.rahier.pouleparty.model.PartyPlansConfig.FlatPlan
import dev.rahier.pouleparty.model.PartyPlansConfig.FreePlan
import dev.rahier.pouleparty.model.PricingModel
import dev.rahier.pouleparty.ui.planselection.PlanSelectionIntent
import dev.rahier.pouleparty.ui.planselection.PlanSelectionUiState
import dev.rahier.pouleparty.ui.planselection.PlanSelectionViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanSelectionViewModelBehaviorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        coEvery { firestoreRepository.fetchPartyPlansConfig() } returns sampleConfig()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleConfig() = PartyPlansConfig(
        free = FreePlan(maxPlayers = 5),
        flat = FlatPlan(minPlayers = 6, maxPlayers = 50, pricePerPlayerCents = 300),
        deposit = DepositPlan(depositAmountCents = 1000, commissionPercent = 15.0)
    )

    private fun createViewModel(): PlanSelectionViewModel {
        return PlanSelectionViewModel(firestoreRepository, auth)
    }

    // MARK: - Plan selection

    @Test
    fun `PlanSelected FLAT updates selectedPlan and sets numberOfPlayers from config`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.FLAT))
        assertEquals(PricingModel.FLAT, vm.uiState.value.selectedPlan)
        assertEquals(6f, vm.uiState.value.numberOfPlayers)
    }

    @Test
    fun `PlanSelected DEPOSIT clears the price field and resets max players flag`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.DEPOSIT))
        assertEquals(PricingModel.DEPOSIT, vm.uiState.value.selectedPlan)
        assertEquals("", vm.uiState.value.pricePerHunter)
        assertFalse(vm.uiState.value.hasMaxPlayers)
    }

    @Test
    fun `PlanSelected FREE just records the choice`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.FREE))
        assertEquals(PricingModel.FREE, vm.uiState.value.selectedPlan)
    }

    @Test
    fun `BackToPlans clears the selected plan`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.FLAT))
        vm.onIntent(PlanSelectionIntent.BackToPlans)
        assertNull(vm.uiState.value.selectedPlan)
    }

    // MARK: - Sliders / fields

    @Test
    fun `NumberOfPlayersChanged updates the slider value`() {
        val vm = createViewModel()
        vm.onIntent(PlanSelectionIntent.NumberOfPlayersChanged(20f))
        assertEquals(20f, vm.uiState.value.numberOfPlayers)
    }

    @Test
    fun `PricePerHunterChanged updates the price text`() {
        val vm = createViewModel()
        vm.onIntent(PlanSelectionIntent.PricePerHunterChanged("5"))
        assertEquals("5", vm.uiState.value.pricePerHunter)
    }

    @Test
    fun `HasMaxPlayersChanged toggles the cap flag`() {
        val vm = createViewModel()
        vm.onIntent(PlanSelectionIntent.HasMaxPlayersChanged(true))
        assertTrue(vm.uiState.value.hasMaxPlayers)
        vm.onIntent(PlanSelectionIntent.HasMaxPlayersChanged(false))
        assertFalse(vm.uiState.value.hasMaxPlayers)
    }

    @Test
    fun `MaxPlayersChanged updates the cap`() {
        val vm = createViewModel()
        vm.onIntent(PlanSelectionIntent.MaxPlayersChanged(40f))
        assertEquals(40f, vm.uiState.value.maxPlayers)
    }

    // MARK: - Daily limit alert

    @Test
    fun `ShowDailyLimitAlert + DismissDailyLimitAlert toggle the flag`() {
        val vm = createViewModel()
        vm.onIntent(PlanSelectionIntent.ShowDailyLimitAlert)
        assertTrue(vm.uiState.value.showDailyLimitAlert)
        vm.onIntent(PlanSelectionIntent.DismissDailyLimitAlert)
        assertFalse(vm.uiState.value.showDailyLimitAlert)
    }

    // MARK: - Step computed

    @Test
    fun `step is CHOOSE_PLAN when no plan selected`() {
        val state = PlanSelectionUiState(selectedPlan = null)
        assertEquals(PlanSelectionUiState.Step.CHOOSE_PLAN, state.step)
    }

    @Test
    fun `step is CONFIGURE_PRICING when plan selected`() {
        val state = PlanSelectionUiState(selectedPlan = PricingModel.FLAT)
        assertEquals(PlanSelectionUiState.Step.CONFIGURE_PRICING, state.step)
    }

    // ── Edge cases ─────────────────────────────────────────

    @Test
    fun `Reload after failure clears loadFailed flag and re-fetches`() {
        coEvery { firestoreRepository.fetchPartyPlansConfig() } throws Exception("network")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.loadFailed)

        coEvery { firestoreRepository.fetchPartyPlansConfig() } returns sampleConfig()
        vm.onIntent(PlanSelectionIntent.Reload)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.loadFailed)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `re-selecting DEPOSIT after editing fields resets nested fields`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.DEPOSIT))
        vm.onIntent(PlanSelectionIntent.PricePerHunterChanged("9"))
        vm.onIntent(PlanSelectionIntent.HasMaxPlayersChanged(true))

        vm.onIntent(PlanSelectionIntent.BackToPlans)
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.DEPOSIT))
        assertEquals("", vm.uiState.value.pricePerHunter)
        assertFalse(vm.uiState.value.hasMaxPlayers)
    }

    @Test
    fun `PricePerHunterChanged accepts non-numeric input without crashing`() {
        val vm = createViewModel()
        vm.onIntent(PlanSelectionIntent.PricePerHunterChanged("abc"))
        assertEquals("abc", vm.uiState.value.pricePerHunter)
    }

    @Test
    fun `MaxPlayersChanged accepts boundary values 0 and large`() {
        val vm = createViewModel()
        vm.onIntent(PlanSelectionIntent.MaxPlayersChanged(0f))
        assertEquals(0f, vm.uiState.value.maxPlayers)
        vm.onIntent(PlanSelectionIntent.MaxPlayersChanged(10_000f))
        assertEquals(10_000f, vm.uiState.value.maxPlayers)
    }

    @Test
    fun `buildNavigationParams returns free defaults when plansConfig is null`() {
        coEvery { firestoreRepository.fetchPartyPlansConfig() } throws Exception("offline")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val params = vm.buildNavigationParams()
        assertEquals("free", params.pricingModel)
        assertEquals(5, params.numberOfPlayers)
        assertEquals(0, params.pricePerPlayerCents)
    }

    @Test
    fun `buildNavigationParams flat uses slider value and config price`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.FLAT))
        vm.onIntent(PlanSelectionIntent.NumberOfPlayersChanged(15f))
        val params = vm.buildNavigationParams()
        assertEquals("flat", params.pricingModel)
        assertEquals(15, params.numberOfPlayers)
        assertEquals(300, params.pricePerPlayerCents)
    }

    @Test
    fun `buildNavigationParams deposit converts price string to cents`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.DEPOSIT))
        vm.onIntent(PlanSelectionIntent.PricePerHunterChanged("7"))
        vm.onIntent(PlanSelectionIntent.HasMaxPlayersChanged(true))
        vm.onIntent(PlanSelectionIntent.MaxPlayersChanged(25f))
        val params = vm.buildNavigationParams()
        assertEquals("deposit", params.pricingModel)
        assertEquals(25, params.numberOfPlayers)
        assertEquals(700, params.pricePerPlayerCents)
        assertEquals(1000, params.depositAmountCents)
    }

    @Test
    fun `buildNavigationParams deposit defaults to 50 players when no cap`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.DEPOSIT))
        vm.onIntent(PlanSelectionIntent.PricePerHunterChanged("5"))
        val params = vm.buildNavigationParams()
        assertEquals(50, params.numberOfPlayers)
    }

    @Test
    fun `buildNavigationParams deposit with empty price gives 0 cents`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(PlanSelectionIntent.PlanSelected(PricingModel.DEPOSIT))
        val params = vm.buildNavigationParams()
        assertEquals(0, params.pricePerPlayerCents)
    }
}
