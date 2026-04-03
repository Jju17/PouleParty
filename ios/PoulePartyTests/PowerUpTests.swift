//
//  PowerUpTests.swift
//  PoulePartyTests
//

import CoreLocation
import Testing
import FirebaseFirestore
@testable import PouleParty

struct PowerUpTests {

    @Test func hunterPowerUpsAreCorrectlyClassified() {
        #expect(PowerUp.PowerUpType.zonePreview.isHunterPowerUp == true)
        #expect(PowerUp.PowerUpType.radarPing.isHunterPowerUp == true)
        #expect(PowerUp.PowerUpType.invisibility.isHunterPowerUp == false)
        #expect(PowerUp.PowerUpType.zoneFreeze.isHunterPowerUp == false)
    }

    @Test func durationValuesAreCorrect() {
        #expect(PowerUp.PowerUpType.zonePreview.durationSeconds == nil)
        #expect(PowerUp.PowerUpType.radarPing.durationSeconds == 10)
        #expect(PowerUp.PowerUpType.invisibility.durationSeconds == 30)
        #expect(PowerUp.PowerUpType.zoneFreeze.durationSeconds == 120)
    }

    @Test func isCollectedReturnsTrueWhenSet() {
        var powerUp = PowerUp.mock
        powerUp.collectedBy = "user123"
        #expect(powerUp.isCollected == true)
    }

    @Test func isCollectedReturnsFalseWhenNil() {
        let powerUp = PowerUp.mock
        #expect(powerUp.isCollected == false)
    }

    @Test func isActivatedReturnsTrueWhenSet() {
        var powerUp = PowerUp.mock
        powerUp.activatedAt = Timestamp(date: .now)
        #expect(powerUp.isActivated == true)
    }

    @Test func isActivatedReturnsFalseWhenNil() {
        let powerUp = PowerUp.mock
        #expect(powerUp.isActivated == false)
    }

    @Test func displayNamesExist() {
        for type in PowerUp.PowerUpType.allCases {
            #expect(!type.displayName.isEmpty)
            #expect(!type.iconName.isEmpty)
        }
    }

    @Test func mockIsValid() {
        let mock = PowerUp.mock
        #expect(!mock.id.isEmpty)
        #expect(mock.type == .radarPing)
        #expect(!mock.isCollected)
        #expect(!mock.isActivated)
    }

    @Test func coordinateIsCorrect() {
        let powerUp = PowerUp(
            id: "test",
            type: .invisibility,
            location: GeoPoint(latitude: 50.8466, longitude: 4.3528),
            spawnedAt: Timestamp(date: .now)
        )
        #expect(abs(powerUp.coordinate.latitude - 50.8466) < 0.0001)
        #expect(abs(powerUp.coordinate.longitude - 4.3528) < 0.0001)
    }
}
