//
//  Game+Computed.swift
//  PouleParty
//

import CoreLocation
import Foundation
import FirebaseFirestore

// MARK: - Coordinate & Date Accessors

extension Game {
    var initialLocation: CLLocationCoordinate2D {
        get {
            self.zone.center.toCLCoordinates
        }
        set {
            let newCoordinates = newValue
            self.zone.center = GeoPoint(latitude: newCoordinates.latitude, longitude: newCoordinates.longitude)
        }
    }

    var finalLocation: CLLocationCoordinate2D? {
        get {
            self.zone.finalCenter?.toCLCoordinates
        }
        set {
            if let newValue {
                self.zone.finalCenter = GeoPoint(latitude: newValue.latitude, longitude: newValue.longitude)
            } else {
                self.zone.finalCenter = nil
            }
        }
    }

    var startDate: Date {
        get {
            self.timing.start.dateValue()
        }
        set {
            // Strip seconds so the start time is always at :00
            let seconds = Calendar.current.component(.second, from: newValue)
            let stripped = newValue.addingTimeInterval(Double(-seconds))
            self.timing.start = Timestamp(date: stripped)
        }
    }

    var endDate: Date {
        get {
            self.timing.end.dateValue()
        }
        set {
            self.timing.end = Timestamp(date: newValue)
        }
    }

    var hunterStartDate: Date {
        startDate.addingTimeInterval(timing.headStartMinutes * 60)
    }

    var gameCode: String {
        String(id.prefix(6)).uppercased()
    }
}

// MARK: - Power-Up Active Effects

extension Game {
    var isChickenInvisible: Bool {
        guard let until = powerUps.activeEffects.invisibility else { return false }
        return .now < until.dateValue()
    }

    var isZoneFrozen: Bool {
        guard let until = powerUps.activeEffects.zoneFreeze else { return false }
        return .now < until.dateValue()
    }

    var isRadarPingActive: Bool {
        guard let until = powerUps.activeEffects.radarPing else { return false }
        return .now < until.dateValue()
    }

    var isDecoyActive: Bool {
        powerUps.activeEffects.decoy.map { Date.now < $0.dateValue() } ?? false
    }

    var isJammerActive: Bool {
        powerUps.activeEffects.jammer.map { Date.now < $0.dateValue() } ?? false
    }

    /// Whether the timed effect associated with this power-up type is
    /// currently active on the game doc. Used to gate activation — a
    /// second activation overwrites `powerUps.activeEffects.<field>`,
    /// shifting the freeze window and desyncing `findLastUpdate`
    /// between Chicken + Hunter (a 1.11.2 live-test report: the Hunter
    /// kept seeing the zone frozen after the Chicken's game already
    /// ended). Blocking the second activation at the UI + reducer
    /// layer prevents that entirely.
    func isActive(effectOf type: PowerUp.PowerUpType) -> Bool {
        switch type {
        case .invisibility: return isChickenInvisible
        case .zoneFreeze:   return isZoneFrozen
        case .radarPing:    return isRadarPingActive
        case .decoy:        return isDecoyActive
        case .jammer:       return isJammerActive
        case .zonePreview:  return false // instant, no timed window
        }
    }
}

// MARK: - Heartbeat

extension Game {
    /// Returns true if the chicken's heartbeat is stale (>60s old),
    /// indicating the chicken may have disconnected.
    var isChickenDisconnected: Bool {
        guard let heartbeat = lastHeartbeat else { return false }
        return Date.now.timeIntervalSince(heartbeat.dateValue()) > 60
    }
}

// MARK: - Game Logic

extension Game {
    static func generateFoundCode() -> String {
        String(format: "%04d", Int.random(in: 0...9999))
    }

    func findLastUpdate() -> (Date, Int) {
        var lastUpdate: Date = self.hunterStartDate
        var lastRadius: Int = Int(self.zone.radius)

        guard zone.shrinkIntervalMinutes > 0 else {
            return (lastUpdate, lastRadius)
        }

        // Zone freeze window: skip radius reductions for shrinks inside [freezeStart, freezeEnd)
        let freezeEnd = powerUps.activeEffects.zoneFreeze?.dateValue()
        let freezeDuration = PowerUp.PowerUpType.zoneFreeze.durationSeconds ?? 0
        let freezeStart = freezeEnd?.addingTimeInterval(-freezeDuration)

        let now = Date.now
        while lastUpdate.addingTimeInterval(TimeInterval(self.zone.shrinkIntervalMinutes * 60)) < now {
            lastUpdate.addTimeInterval(TimeInterval(self.zone.shrinkIntervalMinutes * 60))
            let isFrozen: Bool
            if let fs = freezeStart, let fe = freezeEnd {
                isFrozen = lastUpdate >= fs && lastUpdate < fe
            } else {
                isFrozen = false
            }
            if !isFrozen {
                lastRadius -= Int(self.zone.shrinkMetersPerUpdate)
            }
        }

        lastRadius = max(0, lastRadius)
        let nextUpdate = lastUpdate.addingTimeInterval(TimeInterval(self.zone.shrinkIntervalMinutes * 60))
        return (nextUpdate, lastRadius)
    }
}

// MARK: - Mock

extension Game {
    static var mock: Game {
        Game(
            id: "mock-game-id",
            name: "Mock",
            maxPlayers: 10,
            gameMode: .followTheChicken,
            chickenCanSeeHunters: false,
            foundCode: "1234",
            timing: Timing(
                start: Timestamp(date: .now.addingTimeInterval(300)),
                end: Timestamp(date: .now.addingTimeInterval(3900)),
                headStartMinutes: 0
            ),
            zone: Zone(
                center: GeoPoint(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
                radius: 1500,
                shrinkIntervalMinutes: 5,
                shrinkMetersPerUpdate: 100,
                driftSeed: 42
            ),
            powerUps: GamePowerUps(
                enabled: false,
                enabledTypes: PowerUp.PowerUpType.allCases.map(\.rawValue),
                activeEffects: ActiveEffects()
            )
        )
    }
}
