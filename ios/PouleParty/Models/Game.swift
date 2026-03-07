//
//  Game.swift
//  PouleParty
//
//  Created by Julien Rahier on 16/03/2024.
//

import CoreLocation
import Foundation
import FirebaseFirestore

struct Winner: Codable, Equatable {
    let hunterId: String
    let hunterName: String
    let timestamp: Date
}

struct Game: Codable, Equatable {
    let id: String
    var name: String = ""
    var numberOfPlayers: Int = 10
    var radiusIntervalUpdate: Double = 5 // In minutes
    private(set) var startTimestamp: Timestamp = {
        let date = Date.now.addingTimeInterval(300)
        let seconds = Calendar.current.component(.second, from: date)
        return .init(date: date.addingTimeInterval(Double(-seconds)))
    }()
    private(set) var endTimestamp: Timestamp = .init(date: Date.now.addingTimeInterval(3900))
    private(set) var initialCoordinates: GeoPoint = .init(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)
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

// MARK: - Normal Mode Settings

let normalModeFixedInterval: Double = 5 // minutes
let normalModeMinimumRadius: Double = 100 // meters

func calculateNormalModeSettings(initialRadius: Double, gameDurationMinutes: Double) -> (interval: Double, decline: Double) {
    let numberOfShrinks = gameDurationMinutes / normalModeFixedInterval
    guard numberOfShrinks > 0 else { return (normalModeFixedInterval, 0) }
    let declinePerUpdate = (initialRadius - normalModeMinimumRadius) / numberOfShrinks
    return (normalModeFixedInterval, max(0, declinePerUpdate))
}

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
            driftSeed: 42
        )
    }
}
