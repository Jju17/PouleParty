import ComposableArchitecture
import SwiftUI

struct GameMasterPasswordStep: View {
    @Bindable var store: StoreOf<GameCreationFeature>
    @FocusState private var passwordFieldFocused: Bool

    var body: some View {
        VStack(spacing: 24) {
            SectionHeader(
                title: "Referees",
                subtitle: "A referee can join with a code"
            )

            Toggle(isOn: $store.isGameMasterEnabled) {
                Text("Allow referees")
                    .font(.gameboy(size: 10))
                    .foregroundStyle(Color.onBackground)
            }
            .tint(Color.CROrange)
            .padding(.horizontal, 32)

            if store.isGameMasterEnabled {
                VStack(spacing: 12) {
                    Text("4-digit code")
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.onBackground.opacity(0.6))

                    SecureField("", text: $store.gameMasterPassword)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .multilineTextAlignment(.center)
                        .font(.system(.title, design: .monospaced, weight: .bold))
                        .frame(maxWidth: 200)
                        .padding(.vertical, 12)
                        .background(Color.onBackground.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .focused($passwordFieldFocused)
                        .onChange(of: store.gameMasterPassword) { _, newValue in
                            // Clamp to 4 digits, strip non-digits.
                            let digits = newValue.filter { $0.isNumber }.prefix(4)
                            if digits != Substring(newValue) {
                                store.gameMasterPassword = String(digits)
                            }
                        }

                    Text("Keep it secret: share it only with the referee.")
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.onBackground.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                .onAppear { passwordFieldFocused = true }
                .onDisappear { passwordFieldFocused = false }
            }

        }
    }
}
