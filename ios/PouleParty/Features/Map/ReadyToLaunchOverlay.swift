//
//  ReadyToLaunchOverlay.swift
//  PouleParty
//

import SwiftUI

/// Full-screen overlay shown when `Game.status == .readyToLaunch`
/// (PP-71 manual-start mode). The chicken + GameMaster see a big
/// LAUNCH button that calls the `launchGame` Cloud Function; hunters
/// see a passive waiting state.
struct ReadyToLaunchOverlay: View {
    enum Role: Equatable {
        case launcher
        case waiter
    }

    let role: Role
    let isLaunching: Bool
    let errorMessage: String?
    let onLaunchTapped: () -> Void
    let onErrorDismissed: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.78).ignoresSafeArea()
            VStack(spacing: 24) {
                Text("🐔")
                    .font(.system(size: 80))
                switch role {
                case .launcher:
                    launcherContent
                case .waiter:
                    waiterContent
                }
            }
            .padding(32)
        }
        .alert(
            "Launch failed",
            isPresented: Binding(
                get: { errorMessage != nil },
                set: { newValue in if !newValue { onErrorDismissed() } }
            ),
            presenting: errorMessage
        ) { _ in
            Button("OK", role: .cancel) { onErrorDismissed() }
        } message: { message in
            Text(message)
        }
    }

    @ViewBuilder
    private var launcherContent: some View {
        Text("Ready when you are")
            .font(.title2.bold())
            .foregroundStyle(.white)
            .multilineTextAlignment(.center)
        Text("Tap LAUNCH when everyone is gathered. The countdown starts as soon as you confirm.")
            .font(.subheadline)
            .foregroundStyle(.white.opacity(0.75))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 8)

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
            .padding(.vertical, 22)
            .background(
                LinearGradient(
                    colors: [Color.CROrange, Color.CRPink],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: 18))
        }
        .disabled(isLaunching)
        .padding(.top, 16)
    }

    @ViewBuilder
    private var waiterContent: some View {
        Text("Waiting for the chicken to launch")
            .font(.title3.bold())
            .foregroundStyle(.white)
            .multilineTextAlignment(.center)
        Text("The party will start as soon as the chicken or a GameMaster taps LAUNCH.")
            .font(.subheadline)
            .foregroundStyle(.white.opacity(0.75))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 8)
        ProgressView()
            .progressViewStyle(.circular)
            .tint(.white)
            .scaleEffect(1.3)
            .padding(.top, 8)
    }
}

#Preview("Launcher") {
    ReadyToLaunchOverlay(
        role: .launcher,
        isLaunching: false,
        errorMessage: nil,
        onLaunchTapped: {},
        onErrorDismissed: {}
    )
}

#Preview("Waiter") {
    ReadyToLaunchOverlay(
        role: .waiter,
        isLaunching: false,
        errorMessage: nil,
        onLaunchTapped: {},
        onErrorDismissed: {}
    )
}
