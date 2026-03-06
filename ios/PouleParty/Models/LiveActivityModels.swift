//
//  LiveActivityModels.swift
//  PouleParty
//
//  Shared between PouleParty and PoulePartyWidgets targets.
//

import ActivityKit
import Foundation

struct PoulePartyAttributes: ActivityAttributes {
    let gameName: String
    let gameCode: String
    let playerRole: PlayerRole
    let gameModeName: String
    let gameStartDate: Date
    let gameEndDate: Date
    let totalHunters: Int

    enum PlayerRole: String, Codable {
        case chicken
        case hunter
    }

    struct ContentState: Codable, Hashable {
        let radiusMeters: Int
        let nextShrinkDate: Date?
        let activeHunters: Int
        let winnersCount: Int
        let isOutsideZone: Bool
        let gamePhase: GamePhase

        enum GamePhase: String, Codable {
            case waitingToStart
            case chickenHeadStart
            case hunting
            case gameOver
        }
    }
}
