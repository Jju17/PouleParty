import CoreLocation
import Foundation
import FirebaseFirestore

/// One stored zone circle from `/games/{id}/zone/schedule`; every device
/// renders the same list.
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
    /// uid to role. Written by the server only.
    var roles: [String: String] = [:]
    var hasGameMasterPassword: Bool = false
    var timing: Timing = Timing()
    var zone: Zone = Zone()
    var powerUps: GamePowerUps = GamePowerUps()
    var isAdminCreation: Bool = false
    var manualStartEnabled: Bool = false
    var isDebugGame: Bool = false
    var registrationBatchId: String?

    struct Timing: Codable, Equatable {
        var start: Timestamp = {
            let date = Date.now.addingTimeInterval(7200)
            let seconds = Calendar.current.component(.second, from: date)
            return .init(date: date.addingTimeInterval(Double(-seconds)))
        }()
        var end: Timestamp = .init(date: Date.now.addingTimeInterval(3900))
        var headStartMinutes: Double = 2
        /// Set by the server at LAUNCH for manual-start games.
        var actualStart: Timestamp?
    }

    struct Zone: Codable, Equatable {
        var center: GeoPoint = .init(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)
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
                return String(localized: "Follow the chicken 🐔")
            case .stayInTheZone:
                return String(localized: "Stay in the zone 📍")
            }
        }

        init(from decoder: Decoder) throws {
            let rawValue = try decoder.singleValueContainer().decode(String.self)
            self = GameMode(rawValue: rawValue) ?? .followTheChicken
        }
    }
}

// Missing keys fall back to the property defaults, exactly like Android, so
// a document written before a field existed still decodes.
extension Game {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let defaults = Game(id: "")
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? defaults.name
        maxPlayers = try c.decodeIfPresent(Int.self, forKey: .maxPlayers) ?? defaults.maxPlayers
        gameMode = try c.decodeIfPresent(GameMode.self, forKey: .gameMode) ?? defaults.gameMode
        chickenCanSeeHunters = try c.decodeIfPresent(Bool.self, forKey: .chickenCanSeeHunters) ?? defaults.chickenCanSeeHunters
        foundCode = try c.decodeIfPresent(String.self, forKey: .foundCode) ?? defaults.foundCode
        status = try c.decodeIfPresent(GameStatus.self, forKey: .status) ?? defaults.status
        winners = (try? c.decodeIfPresent([LossyWinner].self, forKey: .winners))?.compactMap(\.winner) ?? []
        creatorId = try c.decodeIfPresent(String.self, forKey: .creatorId) ?? defaults.creatorId
        roles = try c.decodeIfPresent([String: String].self, forKey: .roles) ?? defaults.roles
        hasGameMasterPassword = try c.decodeIfPresent(Bool.self, forKey: .hasGameMasterPassword) ?? false
        timing = try c.decodeIfPresent(Timing.self, forKey: .timing) ?? defaults.timing
        zone = try c.decodeIfPresent(Zone.self, forKey: .zone) ?? defaults.zone
        powerUps = try c.decodeIfPresent(GamePowerUps.self, forKey: .powerUps) ?? defaults.powerUps
        isAdminCreation = try c.decodeIfPresent(Bool.self, forKey: .isAdminCreation) ?? false
        manualStartEnabled = try c.decodeIfPresent(Bool.self, forKey: .manualStartEnabled) ?? false
        isDebugGame = try c.decodeIfPresent(Bool.self, forKey: .isDebugGame) ?? false
        registrationBatchId = try c.decodeIfPresent(String.self, forKey: .registrationBatchId)
    }

    private struct LossyWinner: Decodable {
        let winner: Winner?
        init(from decoder: Decoder) throws {
            winner = try? Winner(from: decoder)
        }
    }
}

extension Game.Timing {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let defaults = Game.Timing()
        start = try c.decodeIfPresent(Timestamp.self, forKey: .start) ?? defaults.start
        end = try c.decodeIfPresent(Timestamp.self, forKey: .end) ?? defaults.end
        headStartMinutes = try c.decodeIfPresent(Double.self, forKey: .headStartMinutes) ?? defaults.headStartMinutes
        actualStart = try c.decodeIfPresent(Timestamp.self, forKey: .actualStart)
    }
}

extension Game.Zone {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let defaults = Game.Zone()
        center = try c.decodeIfPresent(GeoPoint.self, forKey: .center) ?? defaults.center
        startPin = try c.decodeIfPresent(GeoPoint.self, forKey: .startPin)
        finalCenter = try c.decodeIfPresent(GeoPoint.self, forKey: .finalCenter)
        radius = try c.decodeIfPresent(Double.self, forKey: .radius) ?? defaults.radius
        shrinkIntervalMinutes = try c.decodeIfPresent(Double.self, forKey: .shrinkIntervalMinutes) ?? defaults.shrinkIntervalMinutes
        shrinkMetersPerUpdate = try c.decodeIfPresent(Double.self, forKey: .shrinkMetersPerUpdate) ?? defaults.shrinkMetersPerUpdate
        driftSeed = try c.decodeIfPresent(Int.self, forKey: .driftSeed) ?? 0
    }
}

extension Game.GamePowerUps {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? false
        enabledTypes = try c.decodeIfPresent([String].self, forKey: .enabledTypes) ?? Game.GamePowerUps().enabledTypes
        activeEffects = try c.decodeIfPresent(Game.ActiveEffects.self, forKey: .activeEffects) ?? Game.ActiveEffects()
    }
}
