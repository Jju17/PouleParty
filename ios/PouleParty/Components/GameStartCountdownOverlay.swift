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
                    .neonGlow(.CROrange, intensity: .intense)
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

/// Full-screen "lobby" overlay shown on every active map (chicken /
/// hunter / GameMaster) **before** the game starts. Three flavours:
///
/// - **Auto-start (default)**: shows the countdown to `targetDate`
///   (planned `timing.start`). When the countdown reaches the
///   `countdownThresholdSeconds` window the overlay self-hides so
///   the 3-2-1-GO! `GameStartCountdownOverlay` takes over.
/// - **Manual-start launcher** (`isManualStart == true` && role is
///   chicken or GameMaster): replaces the countdown copy with the big
///   LAUNCH button. Tapping calls `onLaunchTapped`. Shows a spinner +
///   "Launching…" while the callable is in flight, and an inline
///   alert via `launchErrorMessage`.
/// - **Manual-start waiter** (`isManualStart == true` && role is
///   hunter): passive "Waiting for the chicken to launch" message.
struct PreGameOverlay: View {
    let role: GameRole
    let gameModTitle: String
    let gameCode: String?
    let targetDate: Date
    let nowDate: Date
    var connectedHunters: Int = 0
    var onCancelGame: (() -> Void)? = nil
    /// PP-71: when true, the overlay shifts from "countdown to start"
    /// into the manual-launch mode (LAUNCH button for chicken/GM,
    /// passive waiter for hunters).
    var isManualStart: Bool = false
    var isLaunching: Bool = false
    var launchErrorMessage: String? = nil
    var onLaunchTapped: (() -> Void)? = nil
    var onLaunchErrorDismissed: (() -> Void)? = nil

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

    /// Hide the auto-start countdown overlay when the 3-2-1 takes
    /// over. The manual-start variant stays visible until the
    /// launcher taps LAUNCH (the parent un-mounts the overlay by
    /// flipping out of `.readyToLaunch`).
    private var isVisible: Bool {
        if isManualStart { return true }
        return secondsRemaining > Int(AppConstants.countdownThresholdSeconds)
    }

    private var isLauncherRole: Bool {
        role == .chicken || role == .gameMaster
    }

    private var headerTitle: String {
        switch role {
        case .chicken: return "You are the 🐔"
        case .hunter: return "You are the Hunter"
        case .gameMaster: return "You are the GameMaster 🦅"
        }
    }

    var body: some View {
        ZStack {
            if isVisible {
                Color.black.opacity(0.7)
                    .ignoresSafeArea()

                VStack(spacing: 24) {
                    BangerText(headerTitle, size: 32)
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

                    if isManualStart {
                        // Push the manual-launch CTA + Cancel button
                        // toward the lower portion of the screen
                        // (~3/4 height) so the launcher's thumb has a
                        // natural reach and the info up top reads as
                        // a header rather than a centered card.
                        Spacer(minLength: 24)
                            .frame(maxHeight: .infinity)
                        manualStartSection

                        if let onCancelGame {
                            Button {
                                onCancelGame()
                            } label: {
                                Text("Cancel game")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundStyle(Color.danger)
                            }
                            .padding(.top, 4)
                        }
                        Spacer().frame(height: 60)
                    } else {
                        VStack(spacing: 8) {
                            Text("Game starts in")
                                .font(.system(size: 14))
                                .foregroundStyle(.white.opacity(0.6))
                            Text(formattedTime)
                                .font(.gameboy(size: timerFontSize))
                                .foregroundStyle(Color.CROrange)
                                .neonGlow(.CROrange, intensity: .medium)
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
                                    .foregroundStyle(Color.danger)
                            }
                            .padding(.top, 8)
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: isManualStart ? .infinity : nil)
                .padding(32)
            }
        }
        .animation(.easeOut(duration: 0.4), value: isVisible)
        .alert(
            "Launch failed",
            isPresented: Binding(
                get: { launchErrorMessage != nil },
                set: { newValue in if !newValue { onLaunchErrorDismissed?() } }
            ),
            presenting: launchErrorMessage
        ) { _ in
            Button("OK", role: .cancel) { onLaunchErrorDismissed?() }
        } message: { message in
            Text(message)
        }
    }

    @ViewBuilder
    private var manualStartSection: some View {
        if isLauncherRole, let onLaunchTapped {
            VStack(spacing: 12) {
                Text("Ready when you are")
                    .font(.system(size: 14))
                    .foregroundStyle(.white.opacity(0.6))
                Button(action: onLaunchTapped) {
                    HStack(spacing: 12) {
                        if isLaunching {
                            ProgressView()
                                .progressViewStyle(.circular)
                                .tint(.white)
                        }
                        Text(isLaunching ? "Launching…" : "LAUNCH GAME")
                            .font(.title.bold())
                            .foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 18)
                    .background(
                        LinearGradient(
                            colors: [Color.CROrange, Color.CRPink],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }
                .disabled(isLaunching)
            }
        } else {
            VStack(spacing: 8) {
                Text("Waiting for the chicken to launch")
                    .font(.system(size: 14))
                    .foregroundStyle(.white.opacity(0.7))
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(.white)
                    .scaleEffect(1.2)
            }
        }
    }
}
