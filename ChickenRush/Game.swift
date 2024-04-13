//
//  Game.swift
//  ChickenRush
//
//  Created by Julien Rahier on 16/03/2024.
//

import CoreLocation
import Foundation
import FirebaseFirestore

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

    enum GameMod: String, CaseIterable, Equatable, Codable {
        case followTheChicken
        case stayInTheZone

        var title: String {
            switch self {
            case .followTheChicken:
                return "Follow the chicken 🐔"
            case .stayInTheZone:
                return "Stay in tha zone 📍"
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

    func findLastUpdate() -> (Date, Int) {
        var lastUpdate: Date = self.startDate
        var lastRadius: Int = Int(self.initialRadius)

        while lastUpdate < .now {
            lastUpdate.addTimeInterval(TimeInterval(self.radiusIntervalUpdate * 60))
            lastRadius -= Int(self.radiusDeclinePerUpdate)
        }

        return (lastUpdate, lastRadius)
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
            gameMod: .followTheChicken
        )
    }
}
