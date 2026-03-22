//
//  GameSettings.swift
//  PouleParty
//

import Foundation

func calculateNormalModeSettings(initialRadius: Double, gameDurationMinutes: Double) -> (interval: Double, decline: Double) {
    let numberOfShrinks = gameDurationMinutes / AppConstants.normalModeFixedInterval
    guard numberOfShrinks > 0 else { return (AppConstants.normalModeFixedInterval, 0) }
    let declinePerUpdate = (initialRadius - AppConstants.normalModeMinimumRadius) / numberOfShrinks
    return (AppConstants.normalModeFixedInterval, max(0, declinePerUpdate))
}
