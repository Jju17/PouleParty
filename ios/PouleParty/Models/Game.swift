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
    /// The player who runs and hides. Set to `creatorId` at game creation,
    /// can be re-designated to any registered hunter by a GameMaster while
    /// `status == waiting` (PP-26). Distinct from `creatorId`, which stays
    /// the game's admin owner.
    var chickenId: String = ""
    /// True when the creator has enabled the GameMaster role and set a
    /// password. The actual password lives in
    /// `/games/{gameId}/private/security` (admin-SDK only, PP-23) — this
    /// flag is the public signal so JoinFlow can show / hide the "Join
    /// as GameMaster" CTA without leaking the password (PP-70).
    var hasGameMasterPassword: Bool = false

    var timing: Timing = Timing()
    var zone: Zone = Zone()
    var powerUps: GamePowerUps = GamePowerUps()
    var lastHeartbeat: Timestamp?
    /// Lifts the `maxPlayers` cap from 5 to 500 for parties created via the
    /// admin code (`jujurahier`). Garde-fou client only — see PP-45 and the
    /// firestore.rules `allow create` clause.
    var isAdminCreation: Bool = false

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
        /// Initial geometric center of the shrinking zone disc. PP-13
        /// recomputes this on the recap step so the first circle
        /// contains BOTH `startPin` and `finalCenter` without being
        /// centered on either — the user-placed start pin sits inside
        /// the disc as a marker, not as its center.
        var center: GeoPoint = .init(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)
        /// PP-11 / PP-13: user-placed start pin. Decoupled from
        /// `center` so the recap can pick a non-centered initial disc
        /// while keeping the visual start marker exactly where the
        /// chicken dropped it. `nil` for legacy games created before
        /// the split — readers fall back to `center` (see
        /// `Game.startPinOrCenter`).
        var startPin: GeoPoint?
        var finalCenter: GeoPoint?
        var radius: Double = 1500
        var shrinkIntervalMinutes: Double = 5
        var shrinkMetersPerUpdate: Double = 100
        var driftSeed: Int = 0
    }

    struct GamePowerUps: Codable, Equatable {
        var enabled: Bool = false
        /// PP-35: defaults shipped to players are intentionally narrow.
        /// Only `zoneFreeze` (chicken) and `zonePreview` (hunter) ship
        /// enabled, so a brand-new party never spawns the noisy
        /// positional power-ups. The other types stay in the enum +
        /// spawner so we can flip them back on per-game from Firestore
        /// without a release.
        var enabledTypes: [String] = [
            PowerUp.PowerUpType.zoneFreeze.rawValue,
            PowerUp.PowerUpType.zonePreview.rawValue,
        ]
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
