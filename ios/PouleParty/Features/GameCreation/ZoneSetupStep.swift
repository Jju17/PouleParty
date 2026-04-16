//
//  ZoneSetupStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct ZoneSetupStep: GameCreationStepView {
    static let step: GameCreationStep = .zoneSetup
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        ChickenMapConfigView(
            store: store.scope(state: \.mapConfigState, action: \.mapConfig)
        )
    }
}
