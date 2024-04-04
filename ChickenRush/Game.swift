//
//  Game.swift
//  ChickenRush
//
//  Created by Julien Rahier on 16/03/2024.
//

import Foundation
import FirebaseFirestore

struct Game: Codable {
    let id: String
    let name: String
    let numberOfPlayers: Int
    let radiusIntervalUpdate: Int // In minutes
    let startTimestamp: Timestamp
    let endTimestamp: Timestamp
    let initialCoordinates: GeoPoint
    let initialRadius: Int
    let radiusDeclinePerUpdate: Int
}

extension Game {
    var startDate: Date {
        self.startTimestamp.dateValue()
    }

    var endDate: Date {
        self.endTimestamp.dateValue()
    }

    func findLastUpdate() -> (Date, Int) {
        var lastUpdate: Date = self.startDate
        var lastRadius: Int = self.initialRadius

        while lastUpdate < .now {
            lastUpdate.addTimeInterval(TimeInterval(self.radiusIntervalUpdate * 60))
            lastRadius -= self.radiusDeclinePerUpdate
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
            radiusDeclinePerUpdate: 100)
    }
}
