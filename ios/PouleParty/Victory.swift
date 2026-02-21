//
//  Victory.swift
//  PouleParty
//
//  Created by Claude on 21/02/2026.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct VictoryFeature {

    @ObservableState
    struct State: Equatable {
        var game: Game
        var hunterId: String
        var hunterName: String
    }

    enum Action {
        case goToMenu
        case onTask
        case gameUpdated(Game)
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .goToMenu:
                return .none
            case .onTask:
                let gameId = state.game.id
                return .run { send in
                    for await game in apiClient.gameConfigStream(gameId) {
                        if let game {
                            await send(.gameUpdated(game))
                        }
                    }
                }
            case let .gameUpdated(game):
                state.game = game
                return .none
            }
        }
    }
}

// MARK: - Victory View

struct VictoryView: View {
    let store: StoreOf<VictoryFeature>

    private var isSpectator: Bool { store.hunterId.isEmpty }

    var body: some View {
        ZStack {
            Color.CRBeige.ignoresSafeArea()

            if !isSpectator {
                ConfettiView()
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }

            VStack(spacing: 24) {
                Spacer()
                    .frame(height: 20)

                Text("🏆")
                    .font(.system(size: 60))

                Text(isSpectator ? "Game Results" : "You found\nthe chicken!")
                    .font(.gameboy(size: 16))
                    .multilineTextAlignment(.center)

                leaderboardSection

                Spacer()

                SelectionButton("BACK TO MENU", color: .black) {
                    store.send(.goToMenu)
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

    private var leaderboardSection: some View {
        let sortedWinners = store.game.winners.sorted {
            $0.timestamp.dateValue() < $1.timestamp.dateValue()
        }
        let remaining = max(0, store.game.hunterIds.count - store.game.winners.count)

        return VStack(spacing: 0) {
            ForEach(Array(sortedWinners.enumerated()), id: \.element.hunterId) { index, winner in
                leaderboardRow(rank: index + 1, winner: winner, isCurrentHunter: winner.hunterId == store.hunterId)
            }

            if remaining > 0 {
                Divider()
                    .padding(.vertical, 12)
                    .padding(.horizontal, 24)

                Text("\(remaining) still in the party 🔍")
                    .font(.banger(size: 18))
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 4)
            }
        }
    }

    private func leaderboardRow(rank: Int, winner: Winner, isCurrentHunter: Bool) -> some View {
        let medal: String = switch rank {
        case 1: "🥇"
        case 2: "🥈"
        case 3: "🥉"
        default: "#\(rank)"
        }

        let timeDelta = winner.timestamp.dateValue().timeIntervalSince(store.game.hunterStartDate)
        let minutes = Int(timeDelta) / 60
        let seconds = Int(timeDelta) % 60
        let timeString = "+\(minutes)m \(String(format: "%02d", seconds))s"

        return HStack {
            Text(medal)
                .font(.system(size: rank <= 3 ? 24 : 16))
                .frame(width: 40)

            Text(winner.hunterName)
                .font(.banger(size: 20))
                .lineLimit(1)

            Spacer()

            Text(timeString)
                .font(.gameboy(size: 10))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 10)
        .background(
            isCurrentHunter
                ? Color.CROrange.opacity(0.2)
                : Color.clear
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, 16)
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

    private static let colors: [Color] = [.CROrange, .CRPink, .yellow, .green, .blue, .red]
    private static let duration: TimeInterval = 10

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let now = timeline.date
                let dt = now.timeIntervalSince(lastTime)

                DispatchQueue.main.async {
                    lastTime = now
                    if isActive {
                        if now.timeIntervalSince(startTime) >= Self.duration {
                            isActive = false
                        }
                        advanceParticles(dt: dt, canvasSize: size, respawn: true)
                    } else {
                        advanceParticles(dt: dt, canvasSize: size, respawn: false)
                    }
                }

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
        }
        .onAppear {
            particles = (0..<80).map { _ in
                ConfettiParticle(
                    x: CGFloat.random(in: 0...1),
                    y: CGFloat.random(in: -1...0),
                    speed: CGFloat.random(in: 0.1...0.4),
                    wobblePhase: CGFloat.random(in: 0...(.pi * 2)),
                    wobbleSpeed: CGFloat.random(in: 1...3),
                    color: Self.colors.randomElement()!,
                    size: CGFloat.random(in: 4...10),
                    rotation: Double.random(in: 0...360),
                    rotationSpeed: Double.random(in: 20...100),
                    shapeType: Int.random(in: 0...1)
                )
            }
        }
    }

    private func advanceParticles(dt: TimeInterval, canvasSize: CGSize, respawn: Bool) {
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
                Winner(hunterId: "h1", hunterName: "Alice", timestamp: .init(date: game.startDate.addingTimeInterval(120))),
                Winner(hunterId: "h2", hunterName: "Bob", timestamp: .init(date: game.startDate.addingTimeInterval(300))),
            ]
            return game
        }(),
        hunterId: "h1",
        hunterName: "Alice"
    )) {
        VictoryFeature()
    })
}
