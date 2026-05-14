//
//  ZoneSetupSteps.swift
//  PouleParty
//
//  PP-11 / PP-12 split the legacy combined `zoneSetup` step into two
//  dedicated steps: `StartZoneSetupStep` places the start pin (plus a
//  3-button size picker in followTheChicken), `FinalZoneSetupStep`
//  places the final pin (stayInTheZone only). Both reuse
//  `ChickenMapConfigView` under the hood; each forces `pinMode` so the
//  user can only edit the pin owned by the active step.
//

import ComposableArchitecture
import SwiftUI

struct StartZoneSetupStep: GameCreationStepView {
    static let step: GameCreationStep = .startZoneSetup
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        ChickenMapConfigView(
            store: store.scope(state: \.mapConfigState, action: \.mapConfig)
        )
        .onAppear {
            store.send(.mapConfig(.pinModeChanged(.start)))
        }
    }
}

struct FinalZoneSetupStep: GameCreationStepView {
    static let step: GameCreationStep = .finalZoneSetup
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        ChickenMapConfigView(
            store: store.scope(state: \.mapConfigState, action: \.mapConfig)
        )
        .onAppear {
            store.send(.mapConfig(.pinModeChanged(.finalZone)))
        }
    }
}
