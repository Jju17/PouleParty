//
//  MockDemoData.swift
//  PouleParty
//

import CoreLocation
import FirebaseFirestore
import Foundation

enum MockDemoData {
    static let chickenUid = "demo-chicken-uid"
    static let hunterIds: [String] = ["demo-hunter-1", "demo-hunter-2", "demo-hunter-3"]
    static let gameMasterUid = "demo-gm-1"

    static let zoneCenter = CLLocationCoordinate2D(latitude: 50.8266, longitude: 4.3528)

    static var liveGame: Game {
        var game = Game(id: "DEMO0000000000000000000000000000")
        game.name = "Demo"
        game.maxPlayers = 5
        game.gameMode = .followTheChicken
        game.chickenCanSeeHunters = true
        game.foundCode = "0000"
        game.hunterIds = hunterIds
        game.gameMasterIds = [gameMasterUid]
        game.status = .inProgress
        game.creatorId = chickenUid
        game.chickenId = chickenUid
        game.winners = []
        game.timing = Game.Timing(
            start: Timestamp(date: .now.addingTimeInterval(-300)),
            end: Timestamp(date: .now.addingTimeInterval(86_400)),
            headStartMinutes: 0
        )
        game.zone = Game.Zone(
            center: GeoPoint(latitude: zoneCenter.latitude, longitude: zoneCenter.longitude),
            startPin: GeoPoint(latitude: zoneCenter.latitude, longitude: zoneCenter.longitude),
            finalCenter: nil,
            radius: 800,
            shrinkIntervalMinutes: 5,
            shrinkMetersPerUpdate: 50,
            driftSeed: 42
        )
        game.powerUps = Game.GamePowerUps(
            enabled: true,
            enabledTypes: [
                PowerUp.PowerUpType.zoneFreeze.rawValue,
                PowerUp.PowerUpType.zonePreview.rawValue,
            ],
            activeEffects: Game.ActiveEffects()
        )
        game.lastHeartbeat = Timestamp(date: .now)
        return game
    }

    static var doneGame: Game {
        var game = liveGame
        // Distinct id so `gameConfigStream` can tell the Victory tab apart
        // from the Chicken / Hunter / GM tabs (which all share `liveGame.id`).
        // Without this, the stream's `gameId == doneGame.id` check matched
        // every demo tab and yielded `status = .done`, triggering the
        // "Game Over" alert on Hunter / GM as soon as `gameConfigUpdated`
        // arrived.
        game.id = "DEMO0000000000000000000000000DONE"
        game.status = .done
        game.winners = [
            Winner(hunterId: hunterIds[0], hunterName: "Red Foxes", timestamp: Timestamp(date: .now.addingTimeInterval(-60)))
        ]
        return game
    }

    static var chickenLocation: ChickenLocation {
        ChickenLocation(
            location: GeoPoint(latitude: zoneCenter.latitude, longitude: zoneCenter.longitude),
            timestamp: Timestamp(date: .now),
            invisible: false
        )
    }

    static var hunterLocations: [HunterLocation] {
        let offsets: [(Double, Double)] = [
            (0.0018, 0.0010),
            (-0.0014, 0.0022),
            (0.0007, -0.0020),
        ]
        return zip(hunterIds, offsets).map { id, off in
            HunterLocation(
                hunterId: id,
                location: GeoPoint(
                    latitude: zoneCenter.latitude + off.0,
                    longitude: zoneCenter.longitude + off.1
                ),
                timestamp: Timestamp(date: .now)
            )
        }
    }

    static var registrations: [Registration] {
        let names = ["Red Foxes", "Blue Wolves", "Green Owls"]
        return zip(hunterIds, names).map { id, name in
            Registration(userId: id, teamName: name)
        }
    }

    static var powerUps: [PowerUp] {
        let placements: [(PowerUp.PowerUpType, Double, Double)] = [
            (.zoneFreeze, 0.0009, 0.0006),
            (.zoneFreeze, -0.0011, 0.0014),
            (.zonePreview, 0.0016, -0.0008),
            (.zonePreview, -0.0007, -0.0015),
            (.zoneFreeze, 0.0004, 0.0021),
        ]
        return placements.enumerated().map { index, item in
            PowerUp(
                id: "demo-powerup-\(index)",
                type: item.0,
                location: GeoPoint(
                    latitude: zoneCenter.latitude + item.1,
                    longitude: zoneCenter.longitude + item.2
                ),
                spawnedAt: Timestamp(date: .now.addingTimeInterval(-30))
            )
        }
    }
}
