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
