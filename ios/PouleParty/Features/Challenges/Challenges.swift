//
//  Challenges.swift
//  PouleParty
//
//  Hunter-only feature presented as a sheet on the HunterMap. Two tabs:
//  a live list of challenges they can validate, and a live leaderboard of
//  all hunters in the game sorted by their total points.
//

import ComposableArchitecture
import os
import SwiftUI

private let logger = Logger(category: "Challenges")

enum ChallengesTab: Equatable {
    case challenges
    case leaderboard
}

/// A single row in the leaderboard — either a hunter who has completed challenges
/// or a hunter in `game.hunterIds` with no completion doc yet (displayed at 0 pts).
struct ChallengeLeaderboardEntry: Equatable, Identifiable {
    let hunterId: String
    let teamName: String
    let totalPoints: Int

    var id: String { hunterId }
}

@Reducer
struct ChallengesFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        let gameId: String
        let hunterId: String
        let hunterIds: [String]
        /// Team name used for the current hunter's completion doc. Pulled from
        /// the hunter's registration (or fallback to nickname) by the parent
        /// before the sheet is presented.
        var myTeamName: String = ""
        var selectedTab: ChallengesTab = .challenges
        var challenges: [Challenge] = []
        var completions: [ChallengeCompletion] = []
        var nicknames: [String: String] = [:]
        var pendingChallenge: Challenge?

        /// Set of challenge IDs the current hunter has already completed.
        var myCompletedIds: Set<String> {
            guard let mine = completions.first(where: { $0.hunterId == hunterId }) else { return [] }
            return Set(mine.completedChallengeIds)
        }

        /// Challenges sorted by highest points desc, then by title for stability.
        var sortedChallenges: [Challenge] {
            challenges.sorted { lhs, rhs in
                if lhs.points != rhs.points { return lhs.points > rhs.points }
                return lhs.title < rhs.title
            }
        }

        /// The leaderboard for all hunters in the game. Hunters without a
        /// completion doc appear with 0 pts. Sorted by points desc, then by
        /// team name for stable ordering on ties.
        var leaderboard: [ChallengeLeaderboardEntry] {
            let byHunter = Dictionary(uniqueKeysWithValues: completions.compactMap { completion -> (String, ChallengeCompletion)? in
                guard let hid = completion.hunterId else { return nil }
                return (hid, completion)
            })
            let entries = hunterIds.map { hid -> ChallengeLeaderboardEntry in
                let completion = byHunter[hid]
                let fallback = nicknames[hid] ?? "Hunter"
                let teamName: String = {
                    if let completion, !completion.teamName.isEmpty { return completion.teamName }
                    return fallback
                }()
                return ChallengeLeaderboardEntry(
                    hunterId: hid,
                    teamName: teamName,
                    totalPoints: completion?.totalPoints ?? 0
                )
            }
            return entries.sorted { lhs, rhs in
                if lhs.totalPoints != rhs.totalPoints { return lhs.totalPoints > rhs.totalPoints }
                return lhs.teamName.localizedCaseInsensitiveCompare(rhs.teamName) == .orderedAscending
            }
        }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case destination(PresentationAction<Destination.Action>)
        case `internal`(Internal)
        case view(View)

        @CasePathable
        enum View {
            case closeTapped
            case onTask
            case validateTapped(Challenge)
            case tabChanged(ChallengesTab)
        }

        @CasePathable
        enum Internal {
            case challengesUpdated([Challenge])
            case completionsUpdated([ChallengeCompletion])
            case nicknamesFetched([String: String])
            case completionWriteFailed
        }
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
        }

        enum Action {
            case alert(Alert)

            enum Alert: Equatable {
                case confirmValidation(Challenge)
            }
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.dismiss) var dismiss

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none

            case let .view(.tabChanged(tab)):
                state.selectedTab = tab
                return .none

            case .view(.closeTapped):
                return .run { _ in await dismiss() }

            case .view(.onTask):
                let gameId = state.gameId
                let hunterIds = state.hunterIds
                return .merge(
                    .run { send in
                        for await challenges in apiClient.challengesStream() {
                            await send(.internal(.challengesUpdated(challenges)))
                        }
                    },
                    .run { send in
                        for await completions in apiClient.challengeCompletionsStream(gameId) {
                            await send(.internal(.completionsUpdated(completions)))
                        }
                    },
                    .run { send in
                        let nicknames = (try? await apiClient.fetchUserNicknames(hunterIds)) ?? [:]
                        await send(.internal(.nicknamesFetched(nicknames)))
                    }
                )

            case let .view(.validateTapped(challenge)):
                guard !state.myCompletedIds.contains(challenge.id) else { return .none }
                state.pendingChallenge = challenge
                state.destination = .alert(
                    AlertState {
                        TextState("Did you send the proof on WhatsApp?")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("Cancel")
                        }
                        ButtonState(action: .confirmValidation(challenge)) {
                            TextState("Confirm")
                        }
                    } message: {
                        TextState("Tap Confirm to validate this challenge.")
                    }
                )
                return .none

            case let .destination(.presented(.alert(.confirmValidation(challenge)))):
                state.pendingChallenge = nil
                let gameId = state.gameId
                let hunterId = state.hunterId
                let teamName = state.myTeamName
                let challengeId = challenge.id
                let points = challenge.points
                return .run { send in
                    do {
                        try await apiClient.markChallengeCompleted(gameId, hunterId, teamName, challengeId, points)
                    } catch {
                        logger.error("markChallengeCompleted failed: \(error.localizedDescription)")
                        await send(.internal(.completionWriteFailed))
                    }
                }

            case .destination:
                return .none

            case let .internal(.challengesUpdated(challenges)):
                state.challenges = challenges
                return .none

            case let .internal(.completionsUpdated(completions)):
                state.completions = completions
                return .none

            case let .internal(.nicknamesFetched(nicknames)):
                state.nicknames = nicknames
                return .none

            case .internal(.completionWriteFailed):
                return .none
            }
        }
        .ifLet(\.$destination, action: \.destination) {
            Destination()
        }
    }
}

// MARK: - View

struct ChallengesView: View {
    @Bindable var store: StoreOf<ChallengesFeature>

    var body: some View {
        VStack(spacing: 0) {
            // Drag indicator — signals swipe-to-dismiss
            Capsule()
                .fill(Color.secondary.opacity(0.35))
                .frame(width: 40, height: 5)
                .padding(.top, 8)
                .padding(.bottom, 4)

            NavigationStack {
                VStack(spacing: 0) {
                    Group {
                        switch store.selectedTab {
                        case .challenges:
                            challengesList
                        case .leaderboard:
                            leaderboardList
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                    bottomPicker
                }
                .navigationTitle("Challenges")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            store.send(.view(.closeTapped))
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title2)
                                .foregroundStyle(.secondary)
                        }
                        .accessibilityLabel("Close")
                    }
                }
            }
        }
        .background(Color.surface)
        .task {
            store.send(.view(.onTask))
        }
        .alert(
            $store.scope(state: \.destination?.alert, action: \.destination.alert)
        )
    }

    // MARK: Challenges tab

    @ViewBuilder
    private var challengesList: some View {
        if store.sortedChallenges.isEmpty {
            VStack {
                Text("No challenges yet.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(store.sortedChallenges) { challenge in
                        ChallengeRow(
                            challenge: challenge,
                            isCompleted: store.myCompletedIds.contains(challenge.id),
                            onValidate: { store.send(.view(.validateTapped(challenge))) }
                        )
                    }
                }
                .padding(16)
            }
        }
    }

    // MARK: Leaderboard tab

    @ViewBuilder
    private var leaderboardList: some View {
        let entries = store.leaderboard
        if entries.isEmpty {
            VStack {
                Text("No hunters yet.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 8) {
                    let top3 = Array(entries.prefix(3))
                    let rest = Array(entries.dropFirst(3))
                    ForEach(Array(top3.enumerated()), id: \.element.id) { index, entry in
                        LeaderboardRow(
                            entry: entry,
                            rank: index + 1,
                            isHighlighted: entry.hunterId == store.hunterId,
                            emphasized: true
                        )
                    }
                    if !rest.isEmpty {
                        Divider()
                            .padding(.vertical, 6)
                        ForEach(Array(rest.enumerated()), id: \.element.id) { index, entry in
                            LeaderboardRow(
                                entry: entry,
                                rank: index + 4,
                                isHighlighted: entry.hunterId == store.hunterId,
                                emphasized: false
                            )
                        }
                    }
                }
                .padding(16)
            }
        }
    }

    // MARK: Fixed-bottom picker

    private var bottomPicker: some View {
        HStack(spacing: 0) {
            tabButton(title: "Challenges", tab: .challenges)
            tabButton(title: "Leaderboard", tab: .leaderboard)
        }
        .padding(6)
        .background(Color.darkBackground.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 16)
    }

    private func tabButton(title: LocalizedStringKey, tab: ChallengesTab) -> some View {
        let selected = store.selectedTab == tab
        return Button {
            store.send(.view(.tabChanged(tab)))
        } label: {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .foregroundStyle(selected ? Color.white : Color.onSurface)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(selected ? Color.CROrange : Color.clear)
                )
        }
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

// MARK: - Rows

private struct ChallengeRow: View {
    let challenge: Challenge
    let isCompleted: Bool
    let onValidate: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(challenge.title)
                    .font(.headline)
                    .bold()
                    .foregroundStyle(Color.onSurface)
                Text(challenge.body)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text("\(challenge.points) pts")
                    .font(.caption)
                    .bold()
                    .foregroundStyle(Color.CROrange)
            }
            Spacer()
            Button(action: onValidate) {
                Text(isCompleted ? "✓ Done" : "Validate")
                    .font(.system(size: 13, weight: .bold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(
                        Capsule().fill(isCompleted ? Color.success.opacity(0.15) : Color.CROrange)
                    )
                    .foregroundStyle(isCompleted ? Color.success : Color.white)
            }
            .disabled(isCompleted)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isCompleted ? Color.success : Color.gray.opacity(0.2), lineWidth: isCompleted ? 2 : 1)
        )
        .neonGlow(isCompleted ? Color.success : Color.clear, intensity: .medium)
    }
}

private struct LeaderboardRow: View {
    let entry: ChallengeLeaderboardEntry
    let rank: Int
    let isHighlighted: Bool
    let emphasized: Bool

    private var medalEmoji: String? {
        switch rank {
        case 1: return "🥇"
        case 2: return "🥈"
        case 3: return "🥉"
        default: return nil
        }
    }

    private var medalTint: Color {
        switch rank {
        case 1: return Color(hex: 0xFFD700)   // gold
        case 2: return Color(hex: 0xC0C0C0)   // silver
        case 3: return Color(hex: 0xCD7F32)   // bronze
        default: return .clear
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            if let medal = medalEmoji {
                Text(medal)
                    .font(.system(size: emphasized ? 28 : 18))
            } else {
                Text("\(rank)")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .frame(minWidth: 24)
            }
            Text(entry.teamName)
                .font(emphasized ? .title3.bold() : .body)
                .foregroundStyle(Color.onSurface)
                .lineLimit(1)
            Spacer()
            Text("\(entry.totalPoints) pts")
                .font(emphasized ? .title3.bold() : .body.bold())
                .foregroundStyle(Color.CROrange)
        }
        .padding(emphasized ? 14 : 10)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isHighlighted ? Color.CROrange.opacity(0.12) : Color.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(
                    isHighlighted ? Color.CROrange : (emphasized ? medalTint.opacity(0.6) : Color.gray.opacity(0.15)),
                    lineWidth: isHighlighted ? 2 : (emphasized ? 2 : 1)
                )
        )
    }
}

#Preview {
    ChallengesView(
        store: Store(
            initialState: ChallengesFeature.State(
                gameId: "preview-game",
                hunterId: "me",
                hunterIds: ["me", "h2", "h3"],
                myTeamName: "Me Team"
            )
        ) {
            ChallengesFeature()
        }
    )
}
