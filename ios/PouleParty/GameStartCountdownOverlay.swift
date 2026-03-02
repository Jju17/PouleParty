//
//  GameStartCountdownOverlay.swift
//  PouleParty
//

import SwiftUI

struct GameStartCountdownOverlay: View {
    let countdownNumber: Int?
    let countdownText: String?

    var body: some View {
        ZStack {
            if let number = countdownNumber {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()

                Text("\(number)")
                    .font(.gameboy(size: 80))
                    .foregroundStyle(Color.CROrange)
                    .id(number)
                    .transition(.scale.combined(with: .opacity))
                    .animation(.easeOut(duration: 0.3), value: number)
            } else if let text = countdownText {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()

                Text(text)
                    .font(.banger(size: 48))
                    .foregroundStyle(Color.CROrange)
                    .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: countdownNumber)
        .animation(.easeInOut(duration: 0.3), value: countdownText)
    }
}

// MARK: - Pre-Game Overlay

struct PreGameOverlay: View {
    let role: PlayerRole
    let gameModTitle: String
    let gameCode: String?
    let targetDate: Date
    let nowDate: Date
    var onCancelGame: (() -> Void)? = nil

    @State private var codeCopied = false

    private var secondsRemaining: Int {
        max(0, Int(targetDate.timeIntervalSince(nowDate)))
    }

    private var formattedTime: String {
        let minutes = secondsRemaining / 60
        let seconds = secondsRemaining % 60
        return String(format: "%d:%02d", minutes, seconds)
    }

    /// Hide when the 3-2-1 countdown takes over
    private var isVisible: Bool {
        secondsRemaining > Int(AppConstants.countdownThresholdSeconds)
    }

    var body: some View {
        ZStack {
            if isVisible {
                Color.black.opacity(0.7)
                    .ignoresSafeArea()

                VStack(spacing: 24) {
                    Text(role == .chicken ? "You are the 🐔" : "You are the Hunter")
                        .font(.banger(size: 32))
                        .foregroundStyle(.white)

                    Text(gameModTitle)
                        .font(.system(size: 16))
                        .foregroundStyle(.white.opacity(0.7))

                    if let code = gameCode {
                        Button {
                            UIPasteboard.general.string = code
                            withAnimation { codeCopied = true }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                withAnimation { codeCopied = false }
                            }
                        } label: {
                            VStack(spacing: 8) {
                                Text(codeCopied ? "Copied!" : "Tap to copy:")
                                    .font(.system(size: 14))
                                    .foregroundStyle(.white.opacity(0.6))
                                HStack(spacing: 12) {
                                    Text(code)
                                        .font(.gameboy(size: 28))
                                        .foregroundStyle(.white)
                                    Image(systemName: codeCopied ? "checkmark" : "doc.on.doc")
                                        .foregroundStyle(codeCopied ? .green : .white.opacity(0.6))
                                        .contentTransition(.symbolEffect(.replace))
                                }
                            }
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 8)
                    }

                    VStack(spacing: 8) {
                        Text("Game starts in")
                            .font(.system(size: 14))
                            .foregroundStyle(.white.opacity(0.6))
                        Text(formattedTime)
                            .font(.gameboy(size: 48))
                            .foregroundStyle(Color.CROrange)
                            .contentTransition(.numericText())
                            .animation(.linear(duration: 0.3), value: secondsRemaining)
                    }
                    .padding(.top, 16)

                    if let onCancelGame {
                        Button {
                            onCancelGame()
                        } label: {
                            Text("Cancel game")
                                .font(.system(size: 16, weight: .medium))
                                .foregroundStyle(.red)
                        }
                        .padding(.top, 8)
                    }
                }
            }
        }
        .animation(.easeOut(duration: 0.4), value: isVisible)
    }
}
