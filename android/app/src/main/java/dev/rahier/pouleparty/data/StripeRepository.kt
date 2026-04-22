package dev.rahier.pouleparty.data

import com.google.firebase.functions.FirebaseFunctions
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.PricingModel
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the Stripe-adjacent Cloud Functions. Never touches
 * Stripe secret keys directly — everything sensitive lives server-side.
 */
@Singleton
class StripeRepository @Inject constructor(
    private val functions: FirebaseFunctions,
) {
    data class CreatorPaymentSheetParams(
        val gameId: String,
        val paymentIntentClientSecret: String,
        val ephemeralKeySecret: String,
        val customerId: String,
        val amountCents: Int,
    )

    data class HunterPaymentSheetParams(
        val paymentIntentClientSecret: String,
        val ephemeralKeySecret: String,
        val customerId: String,
        val amountCents: Int,
    )

    data class PromoCodeValidation(
        val valid: Boolean,
        val promoCodeId: String? = null,
        val percentOff: Double? = null,
        val amountOffCents: Int? = null,
        val freeOverride: Boolean = false,
    )

    /** Forfait creator payment. Pre-creates the game doc in `pending_payment`. */
    suspend fun createCreatorPaymentSheet(
        gameConfig: Game,
        promoCodeId: String?,
    ): CreatorPaymentSheetParams {
        require(gameConfig.pricingModelEnum == PricingModel.FLAT) {
            "createCreatorPaymentSheet only valid for Forfait mode"
        }
        val payload = mutableMapOf<String, Any?>(
            "gameConfig" to gameConfig.toPendingPayload(),
        )
        if (promoCodeId != null) payload["promoCodeId"] = promoCodeId

        val result = functions.getHttpsCallable("createCreatorPaymentSheet").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        val data = (result.getData() as? Map<String, Any?>) ?: error("Malformed response")
        return CreatorPaymentSheetParams(
            gameId = data["gameId"] as String,
            paymentIntentClientSecret = data["paymentIntentClientSecret"] as String,
            ephemeralKeySecret = data["ephemeralKeySecret"] as String,
            customerId = data["customerId"] as String,
            amountCents = (data["amountCents"] as Number).toInt(),
        )
    }

    /** Caution hunter deposit. Server looks up the gameId's pricing.deposit. */
    suspend fun createHunterPaymentSheet(gameId: String): HunterPaymentSheetParams {
        val payload = mapOf("gameId" to gameId)
        val result = functions.getHttpsCallable("createHunterPaymentSheet").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        val data = (result.getData() as? Map<String, Any?>) ?: error("Malformed response")
        return HunterPaymentSheetParams(
            paymentIntentClientSecret = data["paymentIntentClientSecret"] as String,
            ephemeralKeySecret = data["ephemeralKeySecret"] as String,
            customerId = data["customerId"] as String,
            amountCents = (data["amountCents"] as Number).toInt(),
        )
    }

    /** Validates a Stripe Promotion Code. Server re-validates at redemption time. */
    suspend fun validatePromoCode(code: String): PromoCodeValidation {
        val result = functions.getHttpsCallable("validatePromoCode").call(mapOf("code" to code)).await()
        @Suppress("UNCHECKED_CAST")
        val data = (result.getData() as? Map<String, Any?>) ?: error("Malformed response")
        val valid = data["valid"] as? Boolean ?: false
        if (!valid) return PromoCodeValidation(valid = false)
        return PromoCodeValidation(
            valid = true,
            promoCodeId = data["promoCodeId"] as? String,
            percentOff = (data["percentOff"] as? Number)?.toDouble(),
            amountOffCents = (data["amountOff"] as? Number)?.toInt(),
            freeOverride = data["freeOverride"] as? Boolean ?: false,
        )
    }

    /** Creates the game directly when the promo code grants 100% off. */
    suspend fun redeemFreeCreation(gameConfig: Game, promoCodeId: String): String {
        require(gameConfig.pricingModelEnum == PricingModel.FLAT)
        val payload = mapOf(
            "gameConfig" to gameConfig.toPendingPayload(),
            "promoCodeId" to promoCodeId,
        )
        val result = functions.getHttpsCallable("redeemFreeCreation").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        val data = (result.getData() as? Map<String, Any?>) ?: error("Malformed response")
        return data["gameId"] as String
    }
}

/**
 * Flattens a `Game` into the shape expected by the server's `PendingGamePayload`.
 * `Timestamp` is serialised as `{_seconds,_nanoseconds}` by the Functions SDK, which
 * the server doesn't parse — we materialise millis explicitly instead.
 */
private fun Game.toPendingPayload(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "maxPlayers" to maxPlayers,
        "gameMode" to gameMode,
        "chickenCanSeeHunters" to chickenCanSeeHunters,
        "foundCode" to foundCode,
        "timing" to mapOf(
            "startMillis" to timing.start.toDate().time,
            "endMillis" to timing.end.toDate().time,
            "headStartMinutes" to timing.headStartMinutes,
        ),
        "zone" to mapOf(
            "center" to mapOf("latitude" to zone.center.latitude, "longitude" to zone.center.longitude),
            "finalCenter" to (zone.finalCenter?.let { mapOf("latitude" to it.latitude, "longitude" to it.longitude) }
                ?: mapOf("latitude" to zone.center.latitude, "longitude" to zone.center.longitude)),
            "radius" to zone.radius,
            "shrinkIntervalMinutes" to zone.shrinkIntervalMinutes,
            "shrinkMetersPerUpdate" to zone.shrinkMetersPerUpdate,
            "driftSeed" to zone.driftSeed,
        ),
        "pricing" to mapOf(
            "model" to pricing.model,
            "pricePerPlayer" to pricing.pricePerPlayer,
            "deposit" to pricing.deposit,
            "commission" to pricing.commission,
        ),
        "registration" to mapOf(
            "required" to registration.required,
            "closesMinutesBefore" to registration.closesMinutesBefore,
        ),
        "powerUps" to mapOf(
            "enabled" to powerUps.enabled,
            "enabledTypes" to powerUps.enabledTypes,
        ),
    )
}
