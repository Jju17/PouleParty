package dev.rahier.pouleparty.model

data class PartyPlansConfig(
    val free: FreePlan = FreePlan(),
    val flat: FlatPlan = FlatPlan(),
    val deposit: DepositPlan = DepositPlan()
) {
    data class FreePlan(
        val maxPlayers: Int = 5,
        val enabled: Boolean = true
    )

    data class FlatPlan(
        val pricePerPlayerCents: Int = 300,
        val minPlayers: Int = 6,
        val maxPlayers: Int = 50,
        val enabled: Boolean = true
    )

    data class DepositPlan(
        val depositAmountCents: Int = 1000,
        val commissionPercent: Double = 15.0,
        val enabled: Boolean = true
    )
}
