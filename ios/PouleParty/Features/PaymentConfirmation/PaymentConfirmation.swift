//
//  PaymentConfirmation.swift
//  PouleParty
//
//  Shown right after a successful creator Forfait payment (or 100%-off promo
//  redeem) and right after a successful hunter Caution deposit. Celebrates the
//  action, surfaces the game code with a tap-to-copy / share affordance, and
//  lets the player see the countdown to start.
//
//  Without this screen, both flows currently dismiss the payment sheet silently
//  and drop the user back on Home with zero feedback — the paid game creator
//  had no proof anything happened.
//

import ComposableArchitecture
import FirebaseFirestore
import SwiftUI

@Reducer
struct PaymentConfirmationFeature {

    enum Kind: Equatable {
        /// Creator paid the Forfait (either a real PaymentIntent or 100%-off promo).
        case creatorForfait
        /// Hunter paid the Caution deposit at registration time.
        case hunterCaution
    }

    @ObservableState
    struct State: Equatable {
        /// Snapshot of the game at the moment the payment succeeded. Server
        /// webhook may still be flipping `.pendingPayment` → `.waiting`; the
        /// stream from `gameConfigStream` replaces this with the fresh version
        /// as soon as Firestore delivers it.
        var game: Game
        let kind: Kind
        var now: Date = .now
    }

    enum Action {
        case onTask
        case gameUpdated(Game)
        case tick(Date)
        case backToHomeTapped
        case delegate(Delegate)

        @CasePathable
        enum Delegate: Equatable {
            case done
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.continuousClock) var clock

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .onTask:
                let gameId = state.game.id
                return .merge(
                    .run { send in
                        for await game in apiClient.gameConfigStream(gameId) {
                            if let game {
                                await send(.gameUpdated(game))
                            }
                        }
                    },
                    .run { [clock] send in
                        while !Task.isCancelled {
                            do {
                                try await clock.sleep(for: .seconds(1))
                            } catch {
                                // Clock cancelled or failed: exit the loop instead of
                                // spinning at 100% CPU with `try?` swallowing the error.
                                return
                            }
                            await send(.tick(.now))
                        }
                    }
                )

            case let .gameUpdated(game):
                state.game = game
                return .none

            case let .tick(now):
                state.now = now
                return .none

            case .backToHomeTapped:
                return .send(.delegate(.done))

            case .delegate:
                return .none
            }
        }
    }
}

// MARK: - View

struct PaymentConfirmationView: View {
    let store: StoreOf<PaymentConfirmationFeature>

    private var shareMessage: String {
        let code = store.game.gameCode
        let name = store.game.name.isEmpty ? "PouleParty" : store.game.name
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        let when = formatter.string(from: store.game.startDate)
        switch store.kind {
        case .creatorForfait:
            return "Rejoins ma partie \(name) sur Poule Party 🐔 — code \(code) — on joue le \(when)!"
        case .hunterCaution:
            return "Je joue à Poule Party 🐔 — \(name), code \(code), on lance le \(when)!"
        }
    }

    private var title: String {
        switch store.kind {
        case .creatorForfait: return "Partie créée !"
        case .hunterCaution: return "Tu es inscrit !"
        }
    }

    private var subtitle: String {
        switch store.kind {
        case .creatorForfait:
            return "Ton forfait est bien encaissé. Partage le code pour que les chasseurs rejoignent."
        case .hunterCaution:
            return "Ta caution est bien reçue. Rendez-vous le jour J — elle te sera remboursée après la partie."
        }
    }

    private var statusLabel: String {
        switch store.game.status {
        case .waiting: return "En attente des joueurs"
        case .inProgress: return "Partie en cours"
        case .done: return "Partie terminée"
        case .pendingPayment: return "Validation du paiement en cours…"
        case .paymentFailed: return "Paiement échoué"
        }
    }

    private var secondsRemaining: Int {
        max(0, Int(store.game.startDate.timeIntervalSince(store.now)))
    }

    private var countdownString: String {
        let s = secondsRemaining
        if s == 0 { return "La partie peut démarrer" }
        let days = s / 86_400
        let hours = (s % 86_400) / 3_600
        let minutes = (s % 3_600) / 60
        let seconds = s % 60
        if days > 0 {
            return String(format: "%dj %02d:%02d:%02d", days, hours, minutes, seconds)
        } else if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "%d:%02d", minutes, seconds)
        }
    }

    var body: some View {
        ZStack {
            Color.gradientBackgroundWarmth.ignoresSafeArea()

            ConfettiView()
                .ignoresSafeArea()
                .allowsHitTesting(false)

            ScrollView {
                VStack(spacing: 24) {
                    Spacer().frame(height: 32)

                    Text("🎉")
                        .font(.system(size: 72))
                        .accessibilityHidden(true)

                    BangerText(title, size: 36)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(Color.gradientFire)

                    Text(subtitle)
                        .font(.system(size: 15))
                        .foregroundStyle(Color.onBackground.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)

                    gameCard

                    actionButtons
                }
                .padding(24)
            }
        }
        .task {
            store.send(.onTask)
        }
    }

    @ViewBuilder private var gameCard: some View {
        VStack(spacing: 16) {
            if !store.game.name.isEmpty {
                BangerText(store.game.name, size: 24)
                    .foregroundStyle(Color.onBackground)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: 6) {
                Text("Code de partie")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.onBackground.opacity(0.5))
                GameCodeRow(gameCode: store.game.gameCode)
            }

            Divider()
                .padding(.horizontal, 24)

            VStack(spacing: 6) {
                Text("La partie commence dans")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.onBackground.opacity(0.5))
                Text(countdownString)
                    .font(.gameboy(size: 22))
                    .foregroundStyle(Color.CROrange)
                    .contentTransition(.numericText())
                    .animation(.linear(duration: 0.3), value: secondsRemaining)
                Text(store.game.startDate.formatted(date: .abbreviated, time: .shortened))
                    .font(.system(size: 12))
                    .foregroundStyle(Color.onBackground.opacity(0.6))
            }

            HStack(spacing: 6) {
                statusDot
                Text(statusLabel)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color.onBackground.opacity(0.7))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color.surface.opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.onBackground.opacity(0.08), lineWidth: 1)
        )
    }

    @ViewBuilder private var statusDot: some View {
        Circle()
            .fill(statusColor)
            .frame(width: 8, height: 8)
    }

    private var statusColor: Color {
        switch store.game.status {
        case .waiting: return .CROrange
        case .inProgress: return Color(hex: 0x16A34A)
        case .done: return .onBackground.opacity(0.3)
        case .pendingPayment: return .CROrange.opacity(0.6)
        case .paymentFailed: return .red
        }
    }

    @ViewBuilder private var actionButtons: some View {
        VStack(spacing: 12) {
            ShareLink(item: shareMessage) {
                HStack {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 18, weight: .semibold))
                    BangerText("Partager le code", size: 20)
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(Color.gradientFire)
                )
            }

            Button {
                store.send(.backToHomeTapped)
            } label: {
                BangerText("Retour à l'accueil", size: 18)
                    .foregroundStyle(Color.onBackground.opacity(0.75))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
        }
    }
}

#Preview("Creator Forfait") {
    PaymentConfirmationView(
        store: Store(
            initialState: PaymentConfirmationFeature.State(
                game: {
                    var game = Game.mock
                    game.name = "Anniv de Max"
                    game.timing.start = Timestamp(date: Date().addingTimeInterval(3_600 * 5 + 75))
                    return game
                }(),
                kind: .creatorForfait
            )
        ) {
            PaymentConfirmationFeature()
        }
    )
}

#Preview("Hunter Caution") {
    PaymentConfirmationView(
        store: Store(
            initialState: PaymentConfirmationFeature.State(
                game: {
                    var game = Game.mock
                    game.name = "EVG Quentin"
                    game.timing.start = Timestamp(date: Date().addingTimeInterval(3_600 * 48 + 75))
                    return game
                }(),
                kind: .hunterCaution
            )
        ) {
            PaymentConfirmationFeature()
        }
    )
}
