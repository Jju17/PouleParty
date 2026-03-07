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

                BangerText(text, size: 48)
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
    var connectedHunters: Int = 0
    var onCancelGame: (() -> Void)? = nil

    @State private var codeCopied = false

    private var secondsRemaining: Int {
        max(0, Int(targetDate.timeIntervalSince(nowDate)))
    }

    private var days: Int { secondsRemaining / 86400 }
    private var hours: Int { (secondsRemaining % 86400) / 3600 }

    private var formattedTime: String {
        let minutes = (secondsRemaining % 3600) / 60
        let seconds = secondsRemaining % 60
        if days > 0 {
            return String(format: "%dj %02d:%02d:%02d", days, hours, minutes, seconds)
        } else if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "%d:%02d", minutes, seconds)
        }
    }

    private var timerFontSize: CGFloat {
        if days > 0 { return 30 }
        if hours > 0 { return 38 }
        return 48
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
                    BangerText(role == .chicken ? "You are the 🐔" : "You are the Hunter", size: 32)
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

                    VStack(spacing: 6) {
                        HStack(spacing: 6) {
                            Text("🐔")
                                .font(.system(size: 14))
                            Text("1")
                                .font(.gameboy(size: 16))
                                .foregroundStyle(.white)
                            Text("connected")
                                .font(.system(size: 14))
                                .foregroundStyle(.white.opacity(0.6))
                        }
                        HStack(spacing: 6) {
                            Text("🔍")
                                .font(.system(size: 14))
                            Text("\(connectedHunters)")
                                .font(.gameboy(size: 16))
                                .foregroundStyle(.white)
                            Text("connected")
                                .font(.system(size: 14))
                                .foregroundStyle(.white.opacity(0.6))
                        }
                    }
                    .padding(.top, 8)

                    VStack(spacing: 8) {
                        Text("Game starts in")
                            .font(.system(size: 14))
                            .foregroundStyle(.white.opacity(0.6))
                        Text(formattedTime)
                            .font(.gameboy(size: timerFontSize))
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
