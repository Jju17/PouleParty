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
    let timeLimit: Int
    let numberOfPlayers: Int
    let radiusIntervalUpdate: Int
    let gameStartTimestamp: Timestamp
    let initialCoordinates: GeoPoint
    let initialRadius: Int
    let radiusDeclinePerUpdate: Int
}

extension Game {
    var gameStartDate: Date {
        self.gameStartTimestamp.dateValue()
    }
}

extension Game {
    static var mock: Game {
        Game(
            id: UUID().uuidString,
            name: "Mock",
            timeLimit: 60,
            numberOfPlayers: 10,
            radiusIntervalUpdate: 5,
            gameStartTimestamp: Timestamp(date: .now.addingTimeInterval(300)),
            initialCoordinates: GeoPoint(latitude: 50.8466, longitude: 4.3528),
            initialRadius: 1500,
            radiusDeclinePerUpdate: 100)
    }
}
