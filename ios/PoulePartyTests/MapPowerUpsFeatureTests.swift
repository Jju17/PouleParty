//
//  MapPowerUpsFeatureTests.swift
//  PoulePartyTests
//
//  Covers the full behavior of the MapPowerUpsFeature child reducer.
//

import ComposableArchitecture
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
struct MapPowerUpsFeatureTests {

    private func makePowerUp(
        id: String = "pu-1",
        type: PowerUp.PowerUpType = .radarPing
    ) -> PowerUp {
        PowerUp(
            id: id,
            type: type,
            location: GeoPoint(latitude: 50.0, longitude: 4.0),
            spawnedAt: Timestamp(date: .now)
        )
    }

    // MARK: - inventoryTapped / inventoryDismissed

    @Test func inventoryTappedOpensInventory() async {
        let store = TestStore(initialState: MapPowerUpsFeature.State()) {
            MapPowerUpsFeature()
        }

        await store.send(.inventoryTapped) {
            $0.showInventory = true
        }
    }

    @Test func inventoryDismissedClosesInventory() async {
        let store = TestStore(
            initialState: MapPowerUpsFeature.State(showInventory: true)
        ) {
            MapPowerUpsFeature()
        }

        await store.send(.inventoryDismissed) {
            $0.showInventory = false
        }
    }

    // MARK: - dataUpdated

    @Test func dataUpdatedReplacesAvailableAndCollected() async {
        let store = TestStore(initialState: MapPowerUpsFeature.State()) {
            MapPowerUpsFeature()
        }

        let a = makePowerUp(id: "avail-1", type: .radarPing)
        let b = makePowerUp(id: "coll-1", type: .invisibility)

        await store.send(.dataUpdated(available: [a], collected: [b])) {
            $0.available = [a]
            $0.collected = [b]
        }
    }

    @Test func dataUpdatedCanEmptyBothLists() async {
        let p = makePowerUp()
        let store = TestStore(
            initialState: MapPowerUpsFeature.State(available: [p], collected: [p])
        ) {
            MapPowerUpsFeature()
        }

        await store.send(.dataUpdated(available: [], collected: [])) {
            $0.available = []
            $0.collected = []
        }
    }

    // MARK: - notification show / clear

    @Test func notificationShownSetsTextAndType() async {
        let store = TestStore(initialState: MapPowerUpsFeature.State()) {
            MapPowerUpsFeature()
        }

        await store.send(.notificationShown(text: "Jammer activated!", type: .jammer)) {
            $0.notification = "Jammer activated!"
            $0.lastActivatedType = .jammer
        }
    }

    @Test func notificationShownWithNilTypeKeepsTextOnly() async {
        let store = TestStore(initialState: MapPowerUpsFeature.State()) {
            MapPowerUpsFeature()
        }

        await store.send(.notificationShown(text: "Something happened", type: nil)) {
            $0.notification = "Something happened"
            $0.lastActivatedType = nil
        }
    }

    @Test func notificationClearedResetsNotification() async {
        let store = TestStore(
            initialState: MapPowerUpsFeature.State(
                lastActivatedType: .invisibility,
                notification: "Invisibility activated!"
            )
        ) {
            MapPowerUpsFeature()
        }

        await store.send(.notificationCleared) {
            $0.notification = nil
            // lastActivatedType is intentionally preserved to keep the banner's
            // tint consistent until a new notification replaces it.
        }
    }

    // MARK: - activateTapped → delegate

    @Test func activateTappedClosesInventoryShowsNotificationAndEmitsDelegate() async {
        let store = TestStore(
            initialState: MapPowerUpsFeature.State(showInventory: true)
        ) {
            MapPowerUpsFeature()
        }

        let p = makePowerUp(id: "pu-42", type: .zoneFreeze)

        await store.send(.activateTapped(p)) {
            $0.showInventory = false
            $0.notification = "Activated: \(p.type.displayName)!"
            $0.lastActivatedType = .zoneFreeze
        }
        await store.receive(\.delegate.activated)
    }

    @Test func activateTappedForEachTypeProducesExpectedNotificationText() async {
        for type in PowerUp.PowerUpType.allCases {
            let store = TestStore(initialState: MapPowerUpsFeature.State()) {
                MapPowerUpsFeature()
            }
            let p = makePowerUp(id: "pu-\(type.rawValue)", type: type)
            await store.send(.activateTapped(p)) {
                $0.notification = "Activated: \(type.displayName)!"
                $0.lastActivatedType = type
            }
            await store.receive(\.delegate.activated)
        }
    }

    // MARK: - delegate is a no-op in the child

    @Test func delegateActionLeavesStateUntouched() async {
        let initial = MapPowerUpsFeature.State(
            available: [makePowerUp(id: "a")],
            collected: [makePowerUp(id: "b")],
            lastActivatedType: .invisibility,
            notification: "x",
            showInventory: true
        )
        let store = TestStore(initialState: initial) {
            MapPowerUpsFeature()
        }

        await store.send(.delegate(.activated(makePowerUp(id: "whatever"))))
        // State unchanged — TestStore's exhaustivity guarantees no mutation happened.
    }

    // MARK: - Sequence: notificationShown then cleared

    @Test func notificationLifecycleShowThenClear() async {
        let store = TestStore(initialState: MapPowerUpsFeature.State()) {
            MapPowerUpsFeature()
        }

        await store.send(.notificationShown(text: "Radar ping!", type: .radarPing)) {
            $0.notification = "Radar ping!"
            $0.lastActivatedType = .radarPing
        }
        await store.send(.notificationCleared) {
            $0.notification = nil
        }
    }

    // MARK: - Sequence: inventory open → activate → inventory is closed

    @Test func activatingFromOpenInventoryClosesIt() async {
        let store = TestStore(
            initialState: MapPowerUpsFeature.State(
                collected: [makePowerUp(id: "c1", type: .invisibility)],
                showInventory: true
            )
        ) {
            MapPowerUpsFeature()
        }

        let p = makePowerUp(id: "c1", type: .invisibility)
        await store.send(.activateTapped(p)) {
            $0.showInventory = false
            $0.notification = "Activated: \(p.type.displayName)!"
            $0.lastActivatedType = .invisibility
        }
        await store.receive(\.delegate.activated)
    }

    // MARK: - Edge cases

    @Test func notificationShownReplacesPreviousNotification() async {
        let store = TestStore(
            initialState: MapPowerUpsFeature.State(
                lastActivatedType: .radarPing,
                notification: "Old"
            )
        ) {
            MapPowerUpsFeature()
        }

        await store.send(.notificationShown(text: "New", type: .invisibility)) {
            $0.notification = "New"
            $0.lastActivatedType = .invisibility
        }
    }

    @Test func notificationClearedOnEmptyStateIsNoOp() async {
        let store = TestStore(initialState: MapPowerUpsFeature.State()) {
            MapPowerUpsFeature()
        }
        // No state change expected — TestStore exhaustivity guarantees this.
        await store.send(.notificationCleared)
    }

    @Test func inventoryTappedTwiceStaysOpen() async {
        let store = TestStore(initialState: MapPowerUpsFeature.State()) {
            MapPowerUpsFeature()
        }
        await store.send(.inventoryTapped) { $0.showInventory = true }
        // Second tap does not toggle off — it just re-asserts the open state.
        await store.send(.inventoryTapped)
    }

    @Test func dataUpdatedPreservesPreviousNotification() async {
        let initial = MapPowerUpsFeature.State(notification: "Existing")
        let store = TestStore(initialState: initial) { MapPowerUpsFeature() }

        let p = makePowerUp(id: "x")
        await store.send(.dataUpdated(available: [p], collected: [])) {
            $0.available = [p]
            // notification untouched
        }
    }

    @Test func activateTappedWhileNotificationAlreadyShownOverwrites() async {
        let initial = MapPowerUpsFeature.State(
            lastActivatedType: .jammer,
            notification: "Jammer activated!",
            showInventory: true
        )
        let store = TestStore(initialState: initial) { MapPowerUpsFeature() }

        let p = makePowerUp(id: "p2", type: .zoneFreeze)
        await store.send(.activateTapped(p)) {
            $0.showInventory = false
            $0.notification = "Activated: \(p.type.displayName)!"
            $0.lastActivatedType = .zoneFreeze
        }
        await store.receive(\.delegate.activated)
    }

    @Test func dataUpdatedWithIdenticalListsStillReplaces() async {
        let p = makePowerUp(id: "same")
        let store = TestStore(
            initialState: MapPowerUpsFeature.State(available: [p], collected: [p])
        ) { MapPowerUpsFeature() }

        // Even with same content, the action runs without crashing
        // and the lists remain equal.
        await store.send(.dataUpdated(available: [p], collected: [p]))
    }
}
