//
//  Victory.swift
//  PouleParty
//
//  Created by Claude on 21/02/2026.
//

import ComposableArchitecture
import FirebaseFirestore
import SwiftUI

@Reducer
struct VictoryFeature {

    @ObservableState
    struct State: Equatable {
        var game: Game
        var hunterId: String
        var hunterName: String
        var isChicken: Bool = false
        var registrations: [Registration] = []
    }

    enum Action {
        case gameUpdated(Game)
        case menuButtonTapped
        case onTask
        case registrationsLoaded([Registration])
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .menuButtonTapped:
                return .none
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
                    .run { send in
                        let registrations = (try? await apiClient.fetchAllRegistrations(gameId)) ?? []
                        await send(.registrationsLoaded(registrations))
                    }
                )
            case let .gameUpdated(game):
                state.game = game
                return .none
            case let .registrationsLoaded(registrations):
                state.registrations = registrations
                return .none
            }
        }
    }
}

// MARK: - Leaderboard Models

struct LeaderboardEntry: Equatable, Identifiable {
    let id: String // hunterId
    let displayName: String
    let teamName: String?
    let foundTimestamp: Date?
    let isCurrentUser: Bool

    var hasFound: Bool { foundTimestamp != nil }
}

func buildLeaderboardEntries(
    game: Game,
    registrations: [Registration],
    currentUserId: String
) -> [LeaderboardEntry] {
    let registrationByUserId = Dictionary(uniqueKeysWithValues: registrations.map { ($0.userId, $0) })
    let winnerById = Dictionary(uniqueKeysWithValues: game.winners.map { ($0.hunterId, $0) })
    let allHunterIds = Set(game.hunterIds)
        .union(game.winners.map(\.hunterId))
        .union(registrations.map(\.userId))

    return allHunterIds.map { hunterId in
        let registration = registrationByUserId[hunterId]
        let winner = winnerById[hunterId]
        let teamName = registration?.teamName
        let displayName = teamName ?? winner?.hunterName ?? "Hunter"
        return LeaderboardEntry(
            id: hunterId,
            displayName: displayName,
            teamName: teamName,
            foundTimestamp: winner?.timestamp.dateValue(),
            isCurrentUser: hunterId == currentUserId
        )
    }
}

// MARK: - Victory View

struct VictoryView: View {
    let store: StoreOf<VictoryFeature>

    private var isSpectator: Bool { store.hunterId.isEmpty && !store.isChicken }
    private var isCurrentUserAWinner: Bool {
        store.game.winners.contains { $0.hunterId == store.hunterId }
    }

    private var entries: [LeaderboardEntry] {
        let currentUserId = store.isChicken ? "" : store.hunterId
        return buildLeaderboardEntries(
            game: store.game,
            registrations: store.registrations,
            currentUserId: currentUserId
        )
    }

    private var headerTitle: String {
        if store.isChicken { return "Game Over" }
        if isCurrentUserAWinner { return "You found\nthe chicken!" }
        return "Game Results"
    }

    var body: some View {
        ZStack {
            Color.gradientBackgroundWarmth.ignoresSafeArea()

            if isCurrentUserAWinner && !store.isChicken {
                ConfettiView()
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }

            VStack(spacing: 16) {
                Spacer()
                    .frame(height: 20)

                Text(store.isChicken ? "🐔" : "🏆")
                    .font(.system(size: 60))

                Text(headerTitle)
                    .font(.gameboy(size: 16))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Color.onBackground)

                ScrollView {
                    VStack(spacing: 16) {
                        if entries.isEmpty {
                            emptyStateSection
                        } else {
                            LeaderboardContentView(entries: entries, hunterStartDate: store.game.hunterStartDate)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }

                SelectionButton("BACK TO MENU", color: .onBackground) {
                    store.send(.menuButtonTapped)
                }
                .frame(height: 60)
                .padding(.horizontal, 40)
                .padding(.bottom, 32)
            }
        }
        .task {
            store.send(.onTask)
        }
    }

    private var emptyStateSection: some View {
        VStack(spacing: 12) {
            Spacer().frame(height: 60)
            Text("🤷")
                .font(.system(size: 60))
            BangerText("No participants", size: 22)
                .foregroundStyle(Color.onBackground.opacity(0.6))
            Text("This game ended without any hunters joining.")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground.opacity(0.5))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        }
    }

}

// MARK: - Confetti

private struct ConfettiParticle {
    var x: CGFloat
    var y: CGFloat
    var speed: CGFloat
    var wobblePhase: CGFloat
    var wobbleSpeed: CGFloat
    var color: Color
    var size: CGFloat
    var rotation: Double
    var rotationSpeed: Double
    var shapeType: Int // 0 = circle, 1 = rect
}

private struct ConfettiView: View {
    @State private var particles: [ConfettiParticle] = []
    @State private var lastTime: Date = .now
    @State private var startTime: Date = .now
    @State private var isActive: Bool = true

    private static let colors: [Color] = [.CROrange, .CRPink, .chickenYellow, .zoneGreen, .powerupVision, .hunterRed]
    private static let duration: TimeInterval = AppConstants.confettiDurationSeconds

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                for particle in particles {
                    let x = particle.x * size.width
                    let y = particle.y * size.height

                    context.translateBy(x: x, y: y)
                    context.rotate(by: .degrees(particle.rotation))

                    let rect = CGRect(
                        x: -particle.size / 2,
                        y: -particle.size / 2,
                        width: particle.size,
                        height: particle.shapeType == 0 ? particle.size : particle.size * 1.5
                    )

                    if particle.shapeType == 0 {
                        context.fill(
                            Path(ellipseIn: rect),
                            with: .color(particle.color)
                        )
                    } else {
                        context.fill(
                            Path(rect),
                            with: .color(particle.color)
                        )
                    }

                    context.rotate(by: .degrees(-particle.rotation))
                    context.translateBy(x: -x, y: -y)
                }
            }
            .onChange(of: timeline.date) { _, newDate in
                let dt = newDate.timeIntervalSince(lastTime)
                lastTime = newDate
                let respawn = isActive
                if isActive && newDate.timeIntervalSince(startTime) >= Self.duration {
                    isActive = false
                }
                advanceParticles(dt: dt, respawn: respawn)
            }
        }
        .onAppear {
            particles = (0..<AppConstants.confettiParticleCount).map { _ in
                ConfettiParticle(
                    x: CGFloat.random(in: 0...1),
                    y: CGFloat.random(in: -1...0),
                    speed: CGFloat.random(in: 0.1...0.4),
                    wobblePhase: CGFloat.random(in: 0...(.pi * 2)),
                    wobbleSpeed: CGFloat.random(in: 1...3),
                    color: Self.colors.randomElement() ?? .red,
                    size: CGFloat.random(in: 4...10),
                    rotation: Double.random(in: 0...360),
                    rotationSpeed: Double.random(in: 20...100),
                    shapeType: Int.random(in: 0...1)
                )
            }
        }
    }

    private func advanceParticles(dt: TimeInterval, respawn: Bool) {
        particles.removeAll { $0.y > 1.1 && !respawn }

        for i in particles.indices {
            particles[i].y += particles[i].speed * CGFloat(dt)
            particles[i].x += sin(particles[i].wobblePhase) * 0.002
            particles[i].wobblePhase += particles[i].wobbleSpeed * CGFloat(dt)
            particles[i].rotation += particles[i].rotationSpeed * dt

            if particles[i].y > 1.1 && respawn {
                particles[i].y = CGFloat.random(in: -0.2...(-0.05))
                particles[i].x = CGFloat.random(in: 0...1)
            }
        }
    }
}

#Preview {
    VictoryView(store: Store(initialState: VictoryFeature.State(
        game: {
            var game = Game.mock
            game.winners = [
                Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: game.startDate.addingTimeInterval(120))),
                Winner(hunterId: "h2", hunterName: "Bob", timestamp: Timestamp(date: game.startDate.addingTimeInterval(300))),
            ]
            return game
        }(),
        hunterId: "h1",
        hunterName: "Alice"
    )) {
        VictoryFeature()
    })
}
