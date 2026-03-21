//
//  GameSettings.swift
//  PouleParty
//

import Foundation

let normalModeFixedInterval: Double = 5 // minutes
let normalModeMinimumRadius: Double = 100 // meters

func calculateNormalModeSettings(initialRadius: Double, gameDurationMinutes: Double) -> (interval: Double, decline: Double) {
    let numberOfShrinks = gameDurationMinutes / normalModeFixedInterval
    guard numberOfShrinks > 0 else { return (normalModeFixedInterval, 0) }
    let declinePerUpdate = (initialRadius - normalModeMinimumRadius) / numberOfShrinks
    return (normalModeFixedInterval, max(0, declinePerUpdate))
}
