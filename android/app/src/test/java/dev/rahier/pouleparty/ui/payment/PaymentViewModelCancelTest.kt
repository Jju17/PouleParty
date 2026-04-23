package dev.rahier.pouleparty.ui.payment

import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.StripeRepository
import dev.rahier.pouleparty.model.Game
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import io.mockk.verify
import io.mockk.Called

/**
 * Covers the orphan-cleanup path on PaymentSheet cancel / failure.
 *
 * Before the fix, a creator who swipe-dismissed the Stripe sheet left a
 * `pending_payment` game doc in Firestore (created server-side by
 * `createCreatorPaymentSheet`). That doc then showed up in My Games as a
 * ghost "Paiement" entry. The fix deletes the orphan as soon as the sheet
 * result comes back non-completed for the creator flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelCancelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var stripeRepository: StripeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        stripeRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun configured(context: PaymentContext, gameId: String?): PaymentViewModel {
        val vm = PaymentViewModel(
            context = context,
            stripeRepository = stripeRepository,
            firestoreRepository = firestoreRepository,
        )
        // Seed a paymentConfig so onSheetCancelled/Failed have a gameId to work with.
        val sheetConfig = PaymentViewModel.PaymentSheetConfig(
            clientSecret = "pi_secret",
            ephemeralKeySecret = "ek_secret",
            customerId = "cus_123",
            amountCents = 500,
            gameId = gameId,
        )
        // Use the package-private `copy` on UiState via reflection isn't needed
        // because PaymentViewModel exposes state via a MutableStateFlow — we
        // push the payment config through the same code path as in prod.
        val field = PaymentViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<PaymentViewModel.UiState>
        state.value = state.value.copy(paymentConfig = sheetConfig)
        return vm
    }

    @Test
    fun `creator cancel deletes orphan game doc`() {
        coEvery { firestoreRepository.deleteConfig(any()) } returns Unit
        val vm = configured(
            context = PaymentContext.CreatorForfait(gameConfig = Game.mock),
            gameId = "game-abc",
        )

        vm.onSheetCancelled()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { firestoreRepository.deleteConfig("game-abc") }
    }

    @Test
    fun `creator failed deletes orphan game doc`() {
        coEvery { firestoreRepository.deleteConfig(any()) } returns Unit
        val vm = configured(
            context = PaymentContext.CreatorForfait(gameConfig = Game.mock),
            gameId = "game-failed",
        )

        vm.onSheetFailed("Card declined")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { firestoreRepository.deleteConfig("game-failed") }
    }

    @Test
    fun `hunter cancel does NOT delete game doc`() {
        val vm = configured(
            context = PaymentContext.HunterCaution(gameId = "chicken-game"),
            gameId = null,
        )

        vm.onSheetCancelled()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { firestoreRepository wasNot Called }
    }

    @Test
    fun `hunter failed does NOT delete game doc`() {
        val vm = configured(
            context = PaymentContext.HunterCaution(gameId = "chicken-game"),
            gameId = null,
        )

        vm.onSheetFailed("Oops")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { firestoreRepository wasNot Called }
    }

    @Test
    fun `creator cancel with null gameId does nothing`() {
        val vm = configured(
            context = PaymentContext.CreatorForfait(gameConfig = Game.mock),
            gameId = null,
        )

        vm.onSheetCancelled()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { firestoreRepository wasNot Called }
    }

    @Test
    fun `creator cancel swallows delete errors`() {
        coEvery { firestoreRepository.deleteConfig(any()) } throws RuntimeException("offline")
        val vm = configured(
            context = PaymentContext.CreatorForfait(gameConfig = Game.mock),
            gameId = "game-offline",
        )

        // Would crash the test if the error propagated out of viewModelScope.
        vm.onSheetCancelled()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { firestoreRepository.deleteConfig("game-offline") }
    }
}
