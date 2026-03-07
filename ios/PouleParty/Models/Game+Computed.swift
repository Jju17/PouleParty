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
            self.initialCoordinates.toCLCoordinates
        }
        set {
            let newCoordinates = newValue
            self.initialCoordinates = GeoPoint(latitude: newCoordinates.latitude, longitude: newCoordinates.longitude)
        }
    }

    var startDate: Date {
        get {
            self.startTimestamp.dateValue()
        }
        set {
            // Strip seconds so the start time is always at :00
            let seconds = Calendar.current.component(.second, from: newValue)
            let stripped = newValue.addingTimeInterval(Double(-seconds))
            self.startTimestamp = Timestamp(date: stripped)
        }
    }

    var endDate: Date {
        get {
            self.endTimestamp.dateValue()
        }
        set {
            self.endTimestamp = Timestamp(date: newValue)
        }
    }

    var hunterStartDate: Date {
        startDate.addingTimeInterval(chickenHeadStartMinutes * 60)
    }

    var gameCode: String {
        String(id.prefix(6)).uppercased()
    }
}

// MARK: - Power-Up Active Effects

extension Game {
    var isChickenInvisible: Bool {
        guard let until = activeInvisibilityUntil else { return false }
        return .now < until.dateValue()
    }

    var isZoneFrozen: Bool {
        guard let until = activeZoneFreezeUntil else { return false }
        return .now < until.dateValue()
    }

    var isRadarPingActive: Bool {
        guard let until = activeRadarPingUntil else { return false }
        return .now < until.dateValue()
    }
}

// MARK: - Game Logic

extension Game {
    static func generateFoundCode() -> String {
        String(format: "%04d", Int.random(in: 0...9999))
    }

    func findLastUpdate() -> (Date, Int) {
        var lastUpdate: Date = self.hunterStartDate
        var lastRadius: Int = Int(self.initialRadius)

        guard radiusIntervalUpdate > 0 else {
            return (lastUpdate, lastRadius)
        }

        while lastUpdate.addingTimeInterval(TimeInterval(self.radiusIntervalUpdate * 60)) < .now {
            lastUpdate.addTimeInterval(TimeInterval(self.radiusIntervalUpdate * 60))
            lastRadius -= Int(self.radiusDeclinePerUpdate)
        }

        lastRadius = max(0, lastRadius)
        let nextUpdate = lastUpdate.addingTimeInterval(TimeInterval(self.radiusIntervalUpdate * 60))
        return (nextUpdate, lastRadius)
    }
}

// MARK: - Mock

extension Game {
    static var mock: Game {
        Game(
            id: "mock-game-id",
            name: "Mock",
            numberOfPlayers: 10,
            radiusIntervalUpdate: 5,
            startTimestamp: Timestamp(date: .now.addingTimeInterval(300)),
            endTimestamp: Timestamp(date: .now.addingTimeInterval(3900)),
            initialCoordinates: GeoPoint(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
            initialRadius: 1500,
            radiusDeclinePerUpdate: 100,
            chickenHeadStartMinutes: 0,
            gameMod: .followTheChicken,
            chickenCanSeeHunters: false,
            foundCode: "1234",
            driftSeed: 42,
            powerUpsEnabled: false,
            enabledPowerUpTypes: PowerUp.PowerUpType.allCases.map(\.rawValue),
            activeInvisibilityUntil: nil,
            activeZoneFreezeUntil: nil,
            activeRadarPingUntil: nil
        )
    }
}
