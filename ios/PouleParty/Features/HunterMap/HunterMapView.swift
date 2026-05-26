//
//  HunterMapView.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct HunterMapView: View {
    @Bindable var store: StoreOf<HunterMapFeature>
    @State private var selectedPowerUp: PowerUp?
    /// PP-18: manual leaderboard CTA — sheet stays closed by default
    /// at gameOver so the hunter keeps the map view.
    @State private var showLeaderboard: Bool = false
    /// Observes the app's scene phase so we can fire an immediate
    /// hunter-location refresh when the player re-opens the app.
    /// See `.view(.appBecameActive)` in `HunterMapFeature` — iOS can
    /// suspend the background writer coroutine, so we catch the gap
    /// between resume and the next periodic tick here.
    @Environment(\.scenePhase) private var scenePhase

    private var subtitle: String {
        if store.game.chickenCanSeeHunters {
            return "Catch the 🐔 (she sees you! 👀)"
        }
        switch store.game.gameMode {
        case .followTheChicken: return "Catch the 🐔 !"
        case .stayInTheZone:    return "Stay in the zone 📍"
        }
    }

    var body: some View {
        HunterMapContent(store: store, selectedPowerUp: $selectedPowerUp)
            .safeAreaInset(edge: .top) {
                MapTopBar(
                    title: "You are the Hunter",
                    subtitle: subtitle,
                    gradient: LinearGradient(colors: [.hunterRed, .CRPink], startPoint: .leading, endPoint: .trailing),
                    onInfoTapped: { store.send(.view(.infoButtonTapped)) }
                )
            }
            .safeAreaInset(edge: .bottom) {
                MapBottomBar(
                    state: store.state,
                    // PP-18: hunter keeps FOUND active at gameOver so a
                    // straggler can still close the loop. CTA shows next
                    // to it instead of replacing it.
                    isActionButtonVisible: store.hasGameStarted,
                    actionAccessibilityLabel: "I found the chicken",
                    onActionTapped: { store.send(.view(.foundButtonTapped)) },
                    onInventoryTapped: { store.send(.powerUps(.inventoryTapped)) },
                    onLeaderboardTapped: { showLeaderboard = true },
                    isChicken: false
                )
            }
            .overlay(alignment: .bottomTrailing) {
                if store.hasChallenges {
                    ChallengesFabButton(
                        badgeCount: store.pendingChallengeCount,
                        onTap: { store.send(.view(.challengesButtonTapped)) }
                    )
                    .padding(.trailing, 16)
                    // Offset above the bottom bar (MapBottomBar is ~72pt tall including padding)
                    .padding(.bottom, 96)
                }
            }
            .overlay(alignment: .top) {
                if store.hasChallenges,
                   store.hasPendingChallenges,
                   store.hasGameStarted {
                    PendingChallengesBanner(
                        count: store.pendingChallengeCount,
                        onTap: { store.send(.view(.challengesButtonTapped)) }
                    )
                    .padding(.top, 56)
                    .padding(.horizontal, 16)
                }
            }
            .sheet(
                item: $store.scope(state: \.challenges, action: \.challenges)
            ) { challengesStore in
                ChallengesView(store: challengesStore)
                    .presentationDetents([.large])
            }
            .task {
                store.send(.view(.onTask))
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    store.send(.view(.appBecameActive))
                }
            }
            .idleTimerDisabled()
            .mapHaptics(store.state)
            .alert(
                $store.scope(
                    state: \.destination?.alert,
                    action: \.destination.alert
                )
            )
            .alert("Enter Found Code", isPresented: $store.isEnteringFoundCode) {
                TextField("4-digit code", text: $store.enteredCode)
                    .keyboardType(.numberPad)
                Button("Submit") {
                    store.send(.view(.submitCodeButtonTapped))
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Enter the 4-digit code shown by the chicken.")
            }
            .mapCommonOverlays(store.state)
            .overlay(alignment: .top) {
                // PP-36: the "-1 point / 5 s" pill sits just below the
                // red "Return to the zone!" banner. Phase gates mirror
                // the reducer's penalty gate so the indicator and the
                // actual writes can't disagree.
                if store.isOutsideZone,
                   store.hasGameStarted,
                   !store.isGameOver {
                    OutOfZonePenaltyOverlay()
                }
            }
            .overlay {
                if store.game.status == .readyToLaunch {
                    ReadyToLaunchOverlay(
                        role: .waiter,
                        isLaunching: false,
                        errorMessage: nil,
                        onLaunchTapped: {},
                        onErrorDismissed: {}
                    )
                } else if !store.hasGameStarted {
                    PreGameOverlay(
                        role: .hunter,
                        gameModTitle: store.game.gameMode.title,
                        gameCode: nil,
                        targetDate: store.game.hunterStartDate,
                        nowDate: store.nowDate,
                        connectedHunters: store.game.hunterIds.count
                    )
                }
            }
            .mapCommonSheets(
                state: store.state,
                selectedPowerUp: $selectedPowerUp,
                leaveGameLabel: "Leave game",
                onCancelGame: { store.send(.view(.cancelGameButtonTapped)) },
                onGameInfoDismiss: { store.send(.view(.gameInfoDismissed)) },
                onInventoryDismiss: { store.send(.powerUps(.inventoryDismissed)) },
                onActivatePowerUp: { store.send(.powerUps(.activateTapped($0))) }
            )
            // PP-18 — manual leaderboard CTA at gameOver.
            .sheet(isPresented: $showLeaderboard) {
                GameLeaderboardSheet(game: store.game)
            }
    }
}

/// Floating action button that opens the Challenges & Leaderboard sheet.
/// Matches the visual tone (dark translucent material) of the bottom bar.
struct ChallengesFabButton: View {
    let badgeCount: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack {
                Circle()
                    .fill(Color.darkBackground.opacity(0.85))
                Image(systemName: "trophy.fill")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Color.CROrange)
                if badgeCount > 0 {
                    Text("\(badgeCount)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color.CRPink))
                        .offset(x: 16, y: -16)
                }
            }
        }
        .frame(width: 52, height: 52)
        .neonGlow(.CROrange, intensity: .subtle)
        .accessibilityLabel("Challenges and leaderboard")
    }
}

/// PP-21 Phase 2 banner: surfaced when the hunter has un-submitted
/// challenges, taps to open the sheet.
struct PendingChallengesBanner: View {
    let count: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.bubble.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                Group {
                    if count == 1 {
                        Text("1 challenge to submit")
                    } else {
                        Text("\(count) challenges to submit")
                    }
                }
                .font(.subheadline.bold())
                .foregroundStyle(.white)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white.opacity(0.8))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.CRPink, in: RoundedRectangle(cornerRadius: 12))
            .neonGlow(.CRPink, intensity: .subtle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(count) challenges to submit. Tap to open.")
    }
}

#Preview {
    HunterMapView(store: Store(initialState: HunterMapFeature.State(game: .mock)) {
        HunterMapFeature()
    })
}

#Preview("ChallengesFabButton with badge") {
    ChallengesFabButton(badgeCount: 3, onTap: {})
        .padding(40)
        .background(Color.gray.opacity(0.2))
}
