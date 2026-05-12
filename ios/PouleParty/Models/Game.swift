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
    var id: String
    var name: String = ""
    var maxPlayers: Int = 10
    var gameMode: GameMode = .stayInTheZone
    var chickenCanSeeHunters: Bool = false
    var foundCode: String = ""
    var hunterIds: [String] = []
    var gameMasterIds: [String] = []
    var status: GameStatus = .waiting
    var winners: [Winner] = []
    var creatorId: String = ""

    var timing: Timing = Timing()
    var zone: Zone = Zone()
    var registration: GameRegistration = GameRegistration()
    var powerUps: GamePowerUps = GamePowerUps()
    var lastHeartbeat: Timestamp?
    /// Lifts the `maxPlayers` cap from 5 to 500 for parties created via the
    /// admin code (`jujurahier`). Garde-fou client only — see PP-45 and the
    /// firestore.rules `allow create` clause.
    var isAdminCreation: Bool = false

    var registrationDeadline: Date? {
        guard let minutes = registration.closesMinutesBefore else { return nil }
        return timing.start.dateValue().addingTimeInterval(TimeInterval(-minutes * 60))
    }

    var isRegistrationClosed: Bool {
        guard registration.required, let deadline = registrationDeadline else { return false }
        return Date.now >= deadline
    }

    // MARK: - Nested Types

    struct Timing: Codable, Equatable {
        var start: Timestamp = {
            let date = Date.now.addingTimeInterval(7200)
            let seconds = Calendar.current.component(.second, from: date)
            return .init(date: date.addingTimeInterval(Double(-seconds)))
        }()
        var end: Timestamp = .init(date: Date.now.addingTimeInterval(3900))
        var headStartMinutes: Double = 0
    }

    struct Zone: Codable, Equatable {
        var center: GeoPoint = .init(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)
        var finalCenter: GeoPoint?
        var radius: Double = 1500
        var shrinkIntervalMinutes: Double = 5
        var shrinkMetersPerUpdate: Double = 100
        var driftSeed: Int = 0
    }

    struct GameRegistration: Codable, Equatable {
        var required: Bool = false
        var closesMinutesBefore: Int? = 15
    }

    struct GamePowerUps: Codable, Equatable {
        var enabled: Bool = false
        var enabledTypes: [String] = PowerUp.PowerUpType.allCases.map(\.rawValue)
        var activeEffects: ActiveEffects = ActiveEffects()
    }

    struct ActiveEffects: Codable, Equatable {
        var invisibility: Timestamp?
        var zoneFreeze: Timestamp?
        var radarPing: Timestamp?
        var decoy: Timestamp?
        var jammer: Timestamp?
    }

    enum GameStatus: String, CaseIterable, Equatable, Codable {
        case waiting
        case inProgress
        case done

        init(from decoder: Decoder) throws {
            let rawValue = try decoder.singleValueContainer().decode(String.self)
            self = GameStatus(rawValue: rawValue) ?? .waiting
        }
    }

    enum GameMode: String, CaseIterable, Equatable, Codable {
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

        init(from decoder: Decoder) throws {
            let rawValue = try decoder.singleValueContainer().decode(String.self)
            self = GameMode(rawValue: rawValue) ?? .followTheChicken
        }
    }
}
