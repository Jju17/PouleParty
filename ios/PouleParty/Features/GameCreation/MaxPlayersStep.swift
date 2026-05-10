//
//  MaxPlayersStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct MaxPlayersStep: GameCreationStepView {
    static let step: GameCreationStep = .maxPlayers
    @Bindable var store: StoreOf<GameCreationFeature>
    @State private var inputText: String = ""
    @FocusState private var isFocused: Bool

    var body: some View {
        let range = store.maxPlayersRange
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Number of players",
                subtitle: "How many hunters can join?"
            )

            VStack(spacing: 12) {
                ZStack {
                    // Editable: TextField only mounted while focused so the
                    // BangerText kerning trick stays in charge of the static
                    // display (per the project's Bangers-rendering rule).
                    if isFocused {
                        TextField("", text: $inputText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.center)
                            .font(.banger(size: 64))
                            .foregroundStyle(Color.CROrange)
                            .focused($isFocused)
                            .frame(maxWidth: 220)
                    } else {
                        BangerText("\(store.currentGame.maxPlayers)", size: 64)
                            .foregroundStyle(Color.CROrange)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                inputText = "\(store.currentGame.maxPlayers)"
                                isFocused = true
                            }
                            .accessibilityAddTraits(.isButton)
                            .accessibilityHint("Tap to edit the number of players")
                    }
                }
                .frame(height: 80)
                .toolbar {
                    ToolbarItemGroup(placement: .keyboard) {
                        Spacer()
                        Button("Done") { isFocused = false }
                    }
                }

                HStack(spacing: 24) {
                    let canDecrement = store.currentGame.maxPlayers > range.lowerBound
                    let canIncrement = store.currentGame.maxPlayers < range.upperBound
                    Button {
                        store.send(.maxPlayersChanged(store.currentGame.maxPlayers - 1))
                    } label: {
                        Image(systemName: "minus.circle.fill")
                            .font(.system(size: 32))
                            .foregroundStyle(canDecrement ? Color.CROrange : Color.CROrange.opacity(0.3))
                    }
                    .accessibilityLabel("Decrease number of hunters")
                    .disabled(!canDecrement)

                    Button {
                        store.send(.maxPlayersChanged(store.currentGame.maxPlayers + 1))
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 32))
                            .foregroundStyle(canIncrement ? Color.CROrange : Color.CROrange.opacity(0.3))
                    }
                    .accessibilityLabel("Increase number of hunters")
                    .disabled(!canIncrement)
                }

                Text("Between \(range.lowerBound) and \(range.upperBound) hunters")
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.onBackground.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            Spacer()
        }
        .onChange(of: isFocused) { _, focused in
            if !focused {
                commit()
            }
        }
        .onChange(of: inputText) { _, newText in
            // numberPad still allows paste with letters / spaces; keep digits
            // only and cap at 3 chars (max possible value is 500 → 3 digits).
            let sanitized = String(newText.filter(\.isNumber).prefix(3))
            if sanitized != newText {
                inputText = sanitized
            }
        }
    }

    /// Parse the live input and dispatch a clamped update. Empty / unparseable
    /// input is treated as "user changed their mind" — we leave the existing
    /// value untouched.
    private func commit() {
        let trimmed = inputText.trimmingCharacters(in: .whitespaces)
        guard let parsed = Int(trimmed) else { return }
        store.send(.maxPlayersChanged(parsed))
    }
}
