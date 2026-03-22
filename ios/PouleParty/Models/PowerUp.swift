//
//  PowerUp.swift
//  PouleParty
//

import CoreLocation
import Foundation
import FirebaseFirestore
import SwiftUI

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
        case decoy
        case jammer

        var isHunterPowerUp: Bool {
            self == .zonePreview || self == .radarPing
        }

        var color: Color {
            switch self {
            case .invisibility: return .powerupStealth
            case .zoneFreeze: return .powerupFreeze
            case .radarPing: return .powerupRadar
            case .zonePreview: return .powerupVision
            case .decoy: return .powerupSpeed
            case .jammer: return .powerupShield
            }
        }

        var durationSeconds: TimeInterval? {
            switch self {
            case .radarPing: return 30
            case .invisibility: return 30
            case .zoneFreeze: return 120
            case .zonePreview: return nil
            case .decoy: return 20
            case .jammer: return 30
            }
        }

        var displayName: String {
            switch self {
            case .zonePreview: return "Zone Preview"
            case .radarPing: return "Radar Ping"
            case .invisibility: return "Invisibility"
            case .zoneFreeze: return "Zone Freeze"
            case .decoy: return "Decoy"
            case .jammer: return "Jammer"
            }
        }

        var iconName: String {
            switch self {
            case .zonePreview: return "eye.circle.fill"
            case .radarPing: return "antenna.radiowaves.left.and.right"
            case .invisibility: return "eye.slash.circle.fill"
            case .zoneFreeze: return "snowflake.circle.fill"
            case .decoy: return "figure.walk.diamond.fill"
            case .jammer: return "antenna.radiowaves.left.and.right.slash"
            }
        }

        var emoji: String {
            switch self {
            case .invisibility: return "👻"
            case .zoneFreeze: return "❄️"
            case .radarPing: return "📡"
            case .zonePreview: return "🔮"
            case .decoy: return "🎭"
            case .jammer: return "📶"
            }
        }

        var description: String {
            switch self {
            case .zonePreview: return "Shows the next zone boundary before it shrinks"
            case .radarPing: return "Reveals the chicken's position for 30 seconds"
            case .invisibility: return "Hides the chicken from all hunters for 30 seconds"
            case .zoneFreeze: return "Freezes the zone, preventing it from shrinking for 2 minutes"
            case .decoy: return "Places a fake chicken signal on hunter maps for 20 seconds"
            case .jammer: return "Scrambles the chicken's position signal, adding noise for 30 seconds"
            }
        }

        /// Text color that ensures readability on the power-up's background color
        var textColor: Color {
            switch self {
            case .zoneFreeze, .decoy, .jammer: return .black
            case .invisibility, .radarPing, .zonePreview: return .white
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
