//
//  Game.swift
//  PouleParty
//
//  Created by Julien Rahier on 16/03/2024.
//

import CoreLocation
import Foundation
import FirebaseFirestore

struct Game: Codable, Equatable, Identifiable {
    let id: String
    var name: String = ""
    var numberOfPlayers: Int = 10
    var radiusIntervalUpdate: Double = 5 // In minutes
    var startTimestamp: Timestamp = {
        let date = Date.now.addingTimeInterval(7200)
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
    var pricingModel: PricingModel = .free
    var pricePerPlayer: Int = 0 // In cents
    var depositAmount: Int = 0 // In cents
    var commissionPercent: Double = 15.0
    var requiresRegistration: Bool = false
    var powerUpsEnabled: Bool = false
    var enabledPowerUpTypes: [String] = PowerUp.PowerUpType.allCases.map(\.rawValue)
    var activeInvisibilityUntil: Timestamp?
    var activeZoneFreezeUntil: Timestamp?
    var activeRadarPingUntil: Timestamp?
    var activeDecoyUntil: Timestamp?
    var activeJammerUntil: Timestamp?

    var isPaid: Bool { pricingModel != .free }

    enum GameStatus: String, CaseIterable, Equatable, Codable {
        case waiting
        case inProgress
        case done
    }

    enum PricingModel: String, CaseIterable, Equatable, Codable {
        case free
        case flat
        case deposit

        var title: String {
            switch self {
            case .free: return "Free"
            case .flat: return "Forfait"
            case .deposit: return "Caution + %"
            }
        }
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
