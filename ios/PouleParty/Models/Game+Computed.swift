//
//  Game+Computed.swift
//  PouleParty
//

import CoreLocation
import Foundation
import FirebaseFirestore

// MARK: - Coordinate & Date Accessors

extension Game {

    /// The single chicken's uid, or "" when none is set yet.
    var chickenId: String {
        roles.first(where: { $0.value == "chicken" })?.key ?? ""
    }
    /// All hunter uids. Order is not significant (derived from a map).
    var hunterIds: [String] {
        roles.compactMap { $0.value == "hunter" ? $0.key : nil }
    }
    /// All GameMaster uids.
    var gameMasterIds: [String] {
        roles.compactMap { $0.value == "gameMaster" ? $0.key : nil }
    }
    /// This user's role on the game, or nil if they have none.
    func role(of userId: String) -> String? {
        userId.isEmpty ? nil : roles[userId]
    }
    func isChicken(_ userId: String) -> Bool {
        role(of: userId) == "chicken"
    }
    func isHunter(_ userId: String) -> Bool { role(of: userId) == "hunter" }
    func isGameMaster(_ userId: String) -> Bool { role(of: userId) == "gameMaster" }

    var initialLocation: CLLocationCoordinate2D {
        get {
            self.zone.center.toCLCoordinates
        }
        set {
            let newCoordinates = newValue
            self.zone.center = GeoPoint(latitude: newCoordinates.latitude, longitude: newCoordinates.longitude)
        }
    }

    var startPinLocation: CLLocationCoordinate2D {
        get {
            (self.zone.startPin ?? self.zone.center).toCLCoordinates
        }
        set {
            let newCoordinates = newValue
            self.zone.startPin = GeoPoint(latitude: newCoordinates.latitude, longitude: newCoordinates.longitude)
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

    var effectiveStartDate: Date {
        timing.actualStart?.dateValue() ?? startDate
    }

    var hunterStartDate: Date {
        effectiveStartDate.addingTimeInterval(timing.headStartMinutes * 60)
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

    var isRadarPingActive: Bool { isRadarPingActive(at: .now) }
    var isDecoyActive: Bool { isDecoyActive(at: .now) }
    var isJammerActive: Bool { isJammerActive(at: .now) }

    func isRadarPingActive(at now: Date) -> Bool {
        powerUps.activeEffects.radarPing.map { now < $0.dateValue() } ?? false
    }

    func isDecoyActive(at now: Date) -> Bool {
        powerUps.activeEffects.decoy.map { now < $0.dateValue() } ?? false
    }

    func isJammerActive(at now: Date) -> Bool {
        powerUps.activeEffects.jammer.map { now < $0.dateValue() } ?? false
    }

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

// MARK: - Game Logic

extension Game {
    static func generateFoundCode() -> String {
        String(format: "%04d", Int.random(in: 0...9999))
    }

    func findLastUpdate(now: Date = .now) -> (Date, Int) {
        var lastUpdate: Date = self.hunterStartDate
        var lastRadius: Int = Int(self.zone.radius)

        guard zone.shrinkIntervalMinutes > 0 else {
            return (lastUpdate, lastRadius)
        }

        // Zone freeze window: skip radius reductions for shrinks inside [freezeStart, freezeEnd)
        let freezeEnd = powerUps.activeEffects.zoneFreeze?.dateValue()
        let freezeDuration = PowerUp.PowerUpType.zoneFreeze.durationSeconds ?? 0
        let freezeStart = freezeEnd?.addingTimeInterval(-freezeDuration)

        let maxIterations = 10_000
        let interval = TimeInterval(self.zone.shrinkIntervalMinutes * 60)
        var iterations = 0
        while lastUpdate.addingTimeInterval(interval) < now && iterations < maxIterations {
            lastUpdate.addTimeInterval(interval)
            let isFrozen: Bool
            if let fs = freezeStart, let fe = freezeEnd {
                isFrozen = lastUpdate >= fs && lastUpdate < fe
            } else {
                isFrozen = false
            }
            if !isFrozen {
                lastRadius -= Int(self.zone.shrinkMetersPerUpdate)
            }
            // Once the radius is at floor, every later iteration is a
            // no-op, skip them.
            if lastRadius <= 0 { break }
            iterations += 1
        }

        lastRadius = max(0, lastRadius)
        let nextUpdate = lastUpdate.addingTimeInterval(interval)
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
                enabledTypes: [
                    PowerUp.PowerUpType.zoneFreeze.rawValue,
                    PowerUp.PowerUpType.zonePreview.rawValue,
                ],
                activeEffects: ActiveEffects()
            )
        )
    }
}
