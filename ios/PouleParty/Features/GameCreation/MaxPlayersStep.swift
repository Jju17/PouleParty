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
                // Both views are always mounted so `@FocusState` can transfer
                // focus onto the TextField — `if isFocused { TextField } else
                // { BangerText }` would unmount the field before focus can
                // land. Opacity flips the visible one. The TextField uses the
                // raw Bangers font (skipping BangerText's last-char kerning
                // because Text-style rendering is what TextField needs).
                ZStack {
                    BangerText("\(store.currentGame.maxPlayers)", size: 64)
                        .foregroundStyle(Color.CROrange)
                        .opacity(isFocused ? 0 : 1)
                        .allowsHitTesting(!isFocused)

                    TextField("", text: $inputText)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.center)
                        .font(.banger(size: 64))
                        .foregroundStyle(Color.CROrange)
                        .focused($isFocused)
                        .opacity(isFocused ? 1 : 0)
                        .frame(maxWidth: 220)
                }
                .frame(height: 80)
                .contentShape(Rectangle())
                .onTapGesture {
                    if !isFocused {
                        inputText = "\(store.currentGame.maxPlayers)"
                        isFocused = true
                    }
                }
                .accessibilityAddTraits(.isButton)
                .accessibilityHint("Tap to edit the number of players")
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
