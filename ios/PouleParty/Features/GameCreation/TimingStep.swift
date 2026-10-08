import ComposableArchitecture
import SwiftUI

struct TimingStep: GameCreationStepView {
    static let step: GameCreationStep = .timing
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        ScrollView {
            VStack(spacing: 32) {
                StepHeader(title: "How long?", subtitle: "Game length and the chicken's head start")
                DurationStep(store: store)
                HeadStartStep(store: store)
            }
            .padding(.vertical, 24)
        }
    }
}
