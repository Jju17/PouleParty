import ComposableArchitecture
import SwiftUI

struct HeadStartStep: View {
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        VStack(spacing: 24) {
            SectionHeader(
                title: "Head Start",
                subtitle: "Time to hide before the hunt"
            )

            VStack(spacing: 12) {
                BangerText("\(Int(store.currentGame.timing.headStartMinutes)) min", size: 48)
                    .foregroundStyle(Color.CROrange)

                Slider(
                    value: Binding(
                        get: { store.currentGame.timing.headStartMinutes },
                        set: { store.send(.chickenHeadStartChanged($0)) }
                    ),
                    in: 0...15,
                    step: 1
                )
                .tint(Color.CROrange)
                .padding(.horizontal, 40)

                Text("The chicken gets a head start before hunters begin")
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.onBackground.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

        }
    }
}
