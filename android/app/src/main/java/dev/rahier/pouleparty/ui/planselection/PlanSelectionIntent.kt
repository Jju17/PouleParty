package dev.rahier.pouleparty.ui.planselection

import dev.rahier.pouleparty.model.PricingModel

/** User-initiated actions on the plan selection screen. */
sealed interface PlanSelectionIntent {
    object BackToPlans : PlanSelectionIntent
    object DismissDailyLimitAlert : PlanSelectionIntent
    object ShowDailyLimitAlert : PlanSelectionIntent
    object Reload : PlanSelectionIntent
    data class PlanSelected(val plan: PricingModel) : PlanSelectionIntent
    data class NumberOfPlayersChanged(val value: Float) : PlanSelectionIntent
    data class PricePerHunterChanged(val value: String) : PlanSelectionIntent
    data class HasMaxPlayersChanged(val value: Boolean) : PlanSelectionIntent
    data class MaxPlayersChanged(val value: Float) : PlanSelectionIntent
}
