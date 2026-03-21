//
//  PowerUp.swift
//  PouleParty
//

import CoreLocation
import Foundation
import FirebaseFirestore

struct PowerUp: Codable, Equatable, Identifiable {
    let id: String
    let type: PowerUpType
    let location: GeoPoint
    let spawnedAt: Timestamp
    var collectedBy: String?
    var collectedAt: Timestamp?
    var activatedAt: Timestamp?
    var expiresAt: Timestamp?

    var isCollected: Bool { collectedBy != nil }
    var isActivated: Bool { activatedAt != nil }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: location.latitude, longitude: location.longitude)
    }

    enum PowerUpType: String, Codable, CaseIterable, Equatable {
        case zonePreview
        case radarPing
        case invisibility
        case zoneFreeze

        var isHunterPowerUp: Bool {
            self == .zonePreview || self == .radarPing
        }

        var durationSeconds: TimeInterval? {
            switch self {
            case .radarPing: return 10
            case .invisibility: return 30
            case .zoneFreeze: return 120
            case .zonePreview: return nil
            }
        }

        var displayName: String {
            switch self {
            case .zonePreview: return "Zone Preview"
            case .radarPing: return "Radar Ping"
            case .invisibility: return "Invisibility"
            case .zoneFreeze: return "Zone Freeze"
            }
        }

        var iconName: String {
            switch self {
            case .zonePreview: return "eye.circle.fill"
            case .radarPing: return "antenna.radiowaves.left.and.right"
            case .invisibility: return "eye.slash.circle.fill"
            case .zoneFreeze: return "snowflake.circle.fill"
            }
        }

        var description: String {
            switch self {
            case .zonePreview: return "Shows the next zone boundary before it shrinks"
            case .radarPing: return "Reveals the chicken's position for 10 seconds"
            case .invisibility: return "Hides the chicken from all hunters for 30 seconds"
            case .zoneFreeze: return "Freezes the zone, preventing it from shrinking for 2 minutes"
            }
        }

        var targetLabel: String {
            isHunterPowerUp ? "Hunter" : "Chicken"
        }

        var targetEmoji: String {
            isHunterPowerUp ? "🎯" : "🐔"
        }
    }

    static var mock: PowerUp {
        PowerUp(
            id: "mock-powerup",
            type: .radarPing,
            location: GeoPoint(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
            spawnedAt: Timestamp(date: .now)
        )
    }
}
