//
//  Game.swift
//  PouleParty
//
//  Created by Julien Rahier on 16/03/2024.
//

import CoreLocation
import Foundation
import FirebaseFirestore

struct Game: Codable, Equatable {
    let id: String
    var name: String = ""
    var numberOfPlayers: Int = 10
    var radiusIntervalUpdate: Double = 5 // In minutes
    var startTimestamp: Timestamp = {
        let date = Date.now.addingTimeInterval(300)
        let seconds = Calendar.current.component(.second, from: date)
        return .init(date: date.addingTimeInterval(Double(-seconds)))
    }()
    var endTimestamp: Timestamp = .init(date: Date.now.addingTimeInterval(3900))
    var initialCoordinates: GeoPoint = .init(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)
    var finalCoordinates: GeoPoint?
    var initialRadius: Double = 1500
    var radiusDeclinePerUpdate: Double = 100
    var chickenHeadStartMinutes: Double = 0 // In minutes, 0 = no head start
    var gameMod: GameMod = .stayInTheZone
    var chickenCanSeeHunters: Bool = false
    var foundCode: String = ""
    var hunterIds: [String] = []
    var status: GameStatus = .waiting
    var winners: [Winner] = []
    var creatorId: String = ""
    var driftSeed: Int = 0
    var powerUpsEnabled: Bool = false
    var enabledPowerUpTypes: [String] = PowerUp.PowerUpType.allCases.map(\.rawValue)
    var activeInvisibilityUntil: Timestamp?
    var activeZoneFreezeUntil: Timestamp?
    var activeRadarPingUntil: Timestamp?

    enum GameStatus: String, CaseIterable, Equatable, Codable {
        case waiting
        case inProgress
        case done
    }

    enum GameMod: String, CaseIterable, Equatable, Codable {
        case followTheChicken
        case stayInTheZone

        var title: String {
            switch self {
            case .followTheChicken:
                return "Follow the chicken 🐔"
            case .stayInTheZone:
                return "Stay in the zone 📍"
            }
        }
    }
}
