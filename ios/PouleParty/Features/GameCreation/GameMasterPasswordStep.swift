//
//  GameMasterPasswordStep.swift
//  PouleParty
//
//  PP-88: chicken opts in to the GameMaster role and sets a 4-digit
//  password. Server side (PP-70) writes the password to
//  `/games/{gameId}/private/security` and flips
//  `Game.hasGameMasterPassword` to `true` so JoinFlow can show the
//  "Join as GameMaster" CTA.
//

import ComposableArchitecture
import SwiftUI

struct GameMasterPasswordStep: GameCreationStepView {
    static let step: GameCreationStep = .gameMasterPassword
    @Bindable var store: StoreOf<GameCreationFeature>
    @FocusState private var passwordFieldFocused: Bool

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "GameMaster",
                subtitle: "Un arbitre peut rejoindre avec un code"
            )

            Toggle(isOn: $store.isGameMasterEnabled) {
                Text("Activer le rôle GameMaster")
                    .font(.gameboy(size: 10))
                    .foregroundStyle(Color.onBackground)
            }
            .tint(Color.CROrange)
            .padding(.horizontal, 32)

            if store.isGameMasterEnabled {
                VStack(spacing: 12) {
                    Text("Code à 4 chiffres")
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.onBackground.opacity(0.6))

                    SecureField("", text: $store.gameMasterPassword)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .multilineTextAlignment(.center)
                        .font(.system(size: 32, weight: .bold, design: .monospaced))
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

                    Text("Garde-le secret : tu le partages seulement à l'arbitre.")
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.onBackground.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                .onAppear { passwordFieldFocused = true }
                .onDisappear { passwordFieldFocused = false }
            }

            Spacer()
        }
    }
}
