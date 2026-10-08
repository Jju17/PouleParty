import ComposableArchitecture
import SwiftUI

struct OptionsStep: GameCreationStepView {
    static let step: GameCreationStep = .options
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        ScrollView {
            VStack(spacing: 32) {
                StepHeader(
                    title: "Options",
                    subtitle: "Referees, power-ups and visibility. Change them now or keep the defaults."
                )
                GameMasterPasswordStep(store: store)
                if !store.isGameMasterCodeValid {
                    Text("Enter a 4-digit code or turn referees off.")
                        .font(.body)
                        .foregroundStyle(Color.errorText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                        .announcedError(String(localized: "Enter a 4-digit code or turn referees off."))
                }
                PowerUpsStep(store: store)
                ChickenSeesHuntersStep(store: store)
            }
            .padding(.vertical, 24)
        }
    }
}
