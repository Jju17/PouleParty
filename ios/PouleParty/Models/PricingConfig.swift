//
//  PricingConfig.swift
//  PouleParty
//

import Foundation

struct PartyPlansConfig: Codable, Equatable {
    var free: FreePlan = FreePlan()
    var flat: FlatPlan = FlatPlan()
    var deposit: DepositPlan = DepositPlan()

    struct FreePlan: Codable, Equatable {
        var maxPlayers: Int = 5
        var enabled: Bool = true
    }

    struct FlatPlan: Codable, Equatable {
        var pricePerPlayerCents: Int = 300
        var minPlayers: Int = 6
        var maxPlayers: Int = 50
        var enabled: Bool = true
    }

    struct DepositPlan: Codable, Equatable {
        var depositAmountCents: Int = 1000
        var commissionPercent: Double = 15.0
        var enabled: Bool = true
    }
}
