//
//  Game.swift
//  PouleParty
//
//  Created by Julien Rahier on 16/03/2024.
//

import CoreLocation
import Foundation
import FirebaseFirestore

/// PP-zone-stored: one pre-generated zone circle, read from
/// `/games/{id}/zone/schedule` (written server-side by `onGameCreated`).
/// Clients render `circles[shrinkIndex]` read-only instead of recomputing
/// the drift on-device — this is what guarantees every device shows the
/// exact same circle. `radiusMeters` is an exact Double (no Int truncation).
/// In `followTheChicken`, `lat`/`lng` hold the start pin but the runtime
/// uses the live chicken GPS for the center and only takes `radiusMeters`.
struct ZoneCircle: Codable, Equatable {
    var order: Int = 0
    var radiusMeters: Double = 0
    var lat: Double = 0
    var lng: Double = 0

    var center: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}

struct Game: Codable, Equatable, Identifiable {
    var id: String
    var name: String = ""
    var maxPlayers: Int = 10
    var gameMode: GameMode = .stayInTheZone
    var chickenCanSeeHunters: Bool = true
    var foundCode: String = ""
    var status: GameStatus = .waiting
    var winners: [Winner] = []
    var creatorId: String = ""
    /// PP-107: single source of truth for membership. Maps each
    /// participant's uid to their role (`"chicken"` | `"hunter"` |
    /// `"gameMaster"`). A uid has exactly one role, so a "ghost" (no role)
    /// or a double-role is impossible by construction. Written server-side
    /// only (the role callables via admin SDK); `creatorId` stays as
    /// ownership and also appears here with a role. Read through the
    /// computed `chickenId` / `hunterIds` / `gameMasterIds` accessors in
    /// `Game+Computed.swift` — never mutate `roles` from a client.
    var roles: [String: String] = [:]
    /// True when the creator has enabled the GameMaster role and set a
    /// password. The actual password lives in
    /// `/games/{gameId}/private/security` (admin-SDK only, PP-23) — this
    /// flag is the public signal so JoinFlow can show / hide the "Join
    /// as GameMaster" CTA without leaking the password (PP-70).
    var hasGameMasterPassword: Bool = false

    var timing: Timing = Timing()
    var zone: Zone = Zone()
    var powerUps: GamePowerUps = GamePowerUps()
    /// Lifts the `maxPlayers` cap from 5 to 500 for parties created via the
    /// admin code (`jujurahier`). Garde-fou client only — see PP-45 and the
    /// firestore.rules `allow create` clause.
    var isAdminCreation: Bool = false
    /// PP-71: when true, the game waits for an explicit LAUNCH tap from
    /// the chicken or a GameMaster at `timing.start` instead of starting
    /// automatically. Lets the host absorb logistical delays without
    /// burning the planned countdown.
    var manualStartEnabled: Bool = false
    /// QA only: when true the game was created via the `qa_debug_code`
    /// long-press entry. Surfaces the on-map QA debug panel (force end /
    /// spawn power-ups) and pairs with a compressed timing setup. Gated
    /// server-side by the `debugAdvanceGame` callable, which refuses to act
    /// on any game where this is false.
    var isDebugGame: Bool = false
    /// PP-52: when set, this game is linked to a batch of pre-paid web
    /// registrations. The JoinFlow then requires the unique registration code
    /// (validated + single-use-claimed server-side via `validateRegistrationCode`)
    /// before a hunter can join. `nil` for every normal free game.
    var registrationBatchId: String?

    // MARK: - Nested Types

    struct Timing: Codable, Equatable {
        var start: Timestamp = {
            let date = Date.now.addingTimeInterval(7200)
            let seconds = Calendar.current.component(.second, from: date)
            return .init(date: date.addingTimeInterval(Double(-seconds)))
        }()
        var end: Timestamp = .init(date: Date.now.addingTimeInterval(3900))
        var headStartMinutes: Double = 2
        /// PP-71: server-set timestamp of the effective launch when
        /// `manualStartEnabled == true`. `nil` until the LAUNCH callable
        /// fires; read by `hunterStartDate` (and the recomputed `end`)
        /// to anchor every downstream timer on the actual start.
        var actualStart: Timestamp? = nil
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
        /// PP-71: only used when `manualStartEnabled == true`. Reached
        /// at `timing.start`; the LAUNCH callable advances it to
        /// `inProgress` and stamps `timing.actualStart`.
        case readyToLaunch
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
