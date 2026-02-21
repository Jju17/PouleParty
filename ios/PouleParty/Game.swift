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
    let timestamp: Timestamp
}

struct Game: Codable, Equatable {
    let id: String
    var name: String = ""
    var numberOfPlayers: Int = 10
    var radiusIntervalUpdate: Double = 5 // In minutes
    private(set) var startTimestamp: Timestamp = .init(date: Date.now.addingTimeInterval(300))
    private(set) var endTimestamp: Timestamp = .init(date: Date.now.addingTimeInterval(3900))
    private(set) var initialCoordinates: GeoPoint = .init(latitude: 50.8466, longitude: 4.3528)
    var initialRadius: Double = 1500
    var radiusDeclinePerUpdate: Double = 100
    var gameMod: GameMod = .followTheChicken
    var foundCode: String = ""
    var winners: [Winner] = []

    enum GameMod: String, CaseIterable, Equatable, Codable {
        case followTheChicken
        case stayInTheZone
        case mutualTracking

        var title: String {
            switch self {
            case .followTheChicken:
                return "Follow the chicken 🐔"
            case .stayInTheZone:
                return "Stay in tha zone 📍"
            case .mutualTracking:
                return "Mutual tracking 👀"
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
            self.startTimestamp = Timestamp(date: newValue)
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

    var gameCode: String {
        String(id.prefix(6)).uppercased()
    }

    static func generateFoundCode() -> String {
        String(format: "%04d", Int.random(in: 0...9999))
    }

    func findLastUpdate() -> (Date, Int) {
        var lastUpdate: Date = self.startDate
        var lastRadius: Int = Int(self.initialRadius)

        while lastUpdate.addingTimeInterval(TimeInterval(self.radiusIntervalUpdate * 60)) < .now {
            lastUpdate.addTimeInterval(TimeInterval(self.radiusIntervalUpdate * 60))
            lastRadius -= Int(self.radiusDeclinePerUpdate)
        }

        let nextUpdate = lastUpdate.addingTimeInterval(TimeInterval(self.radiusIntervalUpdate * 60))
        return (nextUpdate, lastRadius)
    }
}

extension Game {
    static var mock: Game {
        Game(
            id: UUID().uuidString,
            name: "Mock",
            numberOfPlayers: 10,
            radiusIntervalUpdate: 5,
            startTimestamp: Timestamp(date: .now.addingTimeInterval(300)),
            endTimestamp: Timestamp(date: .now.addingTimeInterval(3900)),
            initialCoordinates: GeoPoint(latitude: 50.8466, longitude: 4.3528),
            initialRadius: 1500,
            radiusDeclinePerUpdate: 100,
            gameMod: .followTheChicken,
            foundCode: "1234"
        )
    }
}
