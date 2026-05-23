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
import PhotosUI
import SwiftUI
import UIKit

private let logger = Logger(category: "Challenges")

enum ChallengesTab: Equatable {
    case challenges
    case leaderboard
}

enum ChallengeStatus: Equatable {
    case available
    case pendingLocal
    case submitting
    case awaitingValidation
    case validated
    case rejected
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
        // Captured at presentation time. Frozen for the lifetime of the
        // sheet : if the game ends while the user keeps the sheet open,
        // submissions stay enabled until the next reopen. Good enough
        // for D-Day; reactive update would need a periodic tick.
        var isClosedForSubmissions: Bool = false
        var selectedTab: ChallengesTab = .challenges
        var challenges: [Challenge] = []
        var completions: [ChallengeCompletion] = []
        var nicknames: [String: String] = [:]
        var pendingChallenge: Challenge?
        var pendingLocalIds: Set<String> = []
        var submittingIds: Set<String> = []
        var mySubmissions: [ChallengeSubmission] = []
        var photoTarget: Challenge?
        var uploadError: String?

        var myCompletedIds: Set<String> {
            guard let mine = completions.first(where: { $0.hunterId == hunterId }) else { return [] }
            return Set(mine.validatedChallengeIds)
        }

        var awaitingValidationIds: Set<String> {
            Set(mySubmissions.filter { $0.status == .pending }.map { $0.challengeId })
        }

        var rejectedIds: Set<String> {
            Set(mySubmissions.filter { $0.status == .rejected }.map { $0.challengeId })
        }

        func status(of challenge: Challenge) -> ChallengeStatus {
            if myCompletedIds.contains(challenge.id) { return .validated }
            if submittingIds.contains(challenge.id) { return .submitting }
            if awaitingValidationIds.contains(challenge.id) { return .awaitingValidation }
            if challenge.type == .oneShot && rejectedIds.contains(challenge.id) { return .rejected }
            if pendingLocalIds.contains(challenge.id) { return .pendingLocal }
            return .available
        }

        /// Challenges sorted by highest points desc, then by title for stability.
        var sortedChallenges: [Challenge] {
            challenges.sorted { lhs, rhs in
                if lhs.points != rhs.points { return lhs.points > rhs.points }
                return lhs.title < rhs.title
            }
        }

        /// Challenges grouped by `level`, ascending. Each group keeps the
        /// same points-desc / title-asc order as `sortedChallenges`. The
        /// UI walks this to render one section per level with a header.
        var challengesByLevel: [(level: Int, challenges: [Challenge])] {
            Dictionary(grouping: challenges) { $0.level }
                .keys.sorted()
                .map { level in
                    let entries = (challenges.filter { $0.level == level }).sorted { lhs, rhs in
                        if lhs.points != rhs.points { return lhs.points > rhs.points }
                        return lhs.title < rhs.title
                    }
                    return (level, entries)
                }
        }

        func isLevelLocked(_ level: Int) -> Bool {
            !ChallengeProgress.isLevelUnlocked(
                level: level,
                challenges: challenges,
                validatedChallengeIds: myCompletedIds
            )
        }

        func progressForLevel(_ level: Int) -> (validated: Int, total: Int, threshold: Int) {
            ChallengeProgress.levelProgress(
                level: level,
                challenges: challenges,
                validatedChallengeIds: myCompletedIds
            )
        }

        /// The leaderboard for all hunters in the game. Hunters without a
        /// completion doc appear with 0 pts. Sorted by points desc, then by
        /// team name for stable ordering on ties.
        var leaderboard: [ChallengeLeaderboardEntry] {
            // CRIT-6 (audit 2026-05-17): uniquingKeysWith — defensive against a
            // future bug that lets two completion docs share the same hunterId.
            // Keeping the entry with the higher totalPoints loses the least info.
            let byHunter = Dictionary(
                completions.compactMap { completion -> (String, ChallengeCompletion)? in
                    guard let hid = completion.hunterId else { return nil }
                    return (hid, completion)
                },
                uniquingKeysWith: { first, second in first.totalPoints >= second.totalPoints ? first : second }
            )
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
            case markAsDoneTapped(Challenge)
            case submitForValidationTapped(Challenge)
            case photoPicked(challengeId: String, data: Data)
            case photoSourceCancelled
            case uploadErrorDismissed
            case tabChanged(ChallengesTab)
        }

        @CasePathable
        enum Internal {
            case challengesUpdated([Challenge])
            case completionsUpdated([ChallengeCompletion])
            case submissionsUpdated([ChallengeSubmission])
            case nicknamesFetched([String: String])
            case submissionWriteSucceeded(challengeId: String)
            case submissionWriteFailed(challengeId: String, message: String)
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
                let hunterId = state.hunterId
                let hunterIds = state.hunterIds
                state.pendingLocalIds = PendingChallengeStore.ids(forGame: gameId)
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
                        for await submissions in apiClient.hunterSubmissionsStream(gameId, hunterId) {
                            await send(.internal(.submissionsUpdated(submissions)))
                        }
                    },
                    .run { send in
                        let nicknames = (try? await apiClient.fetchUserNicknames(hunterIds)) ?? [:]
                        await send(.internal(.nicknamesFetched(nicknames)))
                    }
                )

            case let .view(.markAsDoneTapped(challenge)):
                guard !state.isClosedForSubmissions else { return .none }
                guard state.status(of: challenge) == .available else { return .none }
                let gameId = state.gameId
                state.pendingLocalIds.insert(challenge.id)
                PendingChallengeStore.add(challenge.id, forGame: gameId)
                return .none

            case let .view(.submitForValidationTapped(challenge)):
                guard !state.isClosedForSubmissions else { return .none }
                guard !state.isLevelLocked(challenge.level) else { return .none }
                guard state.status(of: challenge) == .pendingLocal else { return .none }
                state.photoTarget = challenge
                return .none

            case .view(.photoSourceCancelled):
                state.photoTarget = nil
                return .none

            case .view(.uploadErrorDismissed):
                state.uploadError = nil
                return .none

            case let .view(.photoPicked(challengeId, data)):
                state.photoTarget = nil
                guard let challenge = state.challenges.first(where: { $0.id == challengeId }) else { return .none }
                state.submittingIds.insert(challengeId)
                let gameId = state.gameId
                let hunterId = state.hunterId
                let type = challenge.type
                return .run { send in
                    do {
                        _ = try await apiClient.submitChallenge(gameId, challengeId, hunterId, type, data)
                        await send(.internal(.submissionWriteSucceeded(challengeId: challengeId)))
                    } catch {
                        logger.error("submitChallenge failed: \(error.localizedDescription)")
                        await send(.internal(.submissionWriteFailed(challengeId: challengeId, message: error.localizedDescription)))
                    }
                }

            case .destination:
                return .none

            case let .internal(.challengesUpdated(challenges)):
                state.challenges = challenges
                return .none

            case let .internal(.completionsUpdated(completions)):
                state.completions = completions
                for id in state.myCompletedIds where state.pendingLocalIds.contains(id) {
                    state.pendingLocalIds.remove(id)
                    PendingChallengeStore.remove(id, forGame: state.gameId)
                }
                return .none

            case let .internal(.submissionsUpdated(submissions)):
                state.mySubmissions = submissions
                let pendingOrValidated = Set(submissions.filter { $0.status != .rejected }.map { $0.challengeId })
                for id in pendingOrValidated where state.pendingLocalIds.contains(id) {
                    state.pendingLocalIds.remove(id)
                    PendingChallengeStore.remove(id, forGame: state.gameId)
                }
                return .none

            case let .internal(.nicknamesFetched(nicknames)):
                state.nicknames = nicknames
                return .none

            case let .internal(.submissionWriteSucceeded(challengeId)):
                state.submittingIds.remove(challengeId)
                state.pendingLocalIds.remove(challengeId)
                PendingChallengeStore.remove(challengeId, forGame: state.gameId)
                return .none

            case let .internal(.submissionWriteFailed(challengeId, message)):
                state.submittingIds.remove(challengeId)
                state.uploadError = message
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
    @State private var photoSource: PhotoSource?
    @State private var pickerItem: PhotosPickerItem?

    enum PhotoSource: Identifiable {
        case library
        case camera
        var id: Int { self == .library ? 0 : 1 }
    }

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
        .confirmationDialog(
            "Add a photo",
            isPresented: Binding(
                get: { store.photoTarget != nil },
                set: { newValue in
                    if !newValue { store.send(.view(.photoSourceCancelled)) }
                }
            ),
            titleVisibility: .visible
        ) {
            Button("Take a photo") { photoSource = .camera }
            Button("Choose from library") { photoSource = .library }
            Button("Cancel", role: .cancel) {
                store.send(.view(.photoSourceCancelled))
            }
        }
        .sheet(item: $photoSource) { source in
            switch source {
            case .library:
                PhotosPicker(
                    selection: $pickerItem,
                    matching: .images,
                    photoLibrary: .shared()
                ) {
                    Color.surface.ignoresSafeArea()
                }
                .photosPickerStyle(.inline)
                .ignoresSafeArea()
            case .camera:
                CameraPicker { data in
                    photoSource = nil
                    if let data, let challenge = store.photoTarget {
                        store.send(.view(.photoPicked(challengeId: challenge.id, data: data)))
                    }
                }
                .ignoresSafeArea()
            }
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self),
                      let image = UIImage(data: data),
                      let jpeg = image.jpegDataResized() else {
                    pickerItem = nil
                    photoSource = nil
                    return
                }
                if let challenge = store.photoTarget {
                    store.send(.view(.photoPicked(challengeId: challenge.id, data: jpeg)))
                }
                pickerItem = nil
                photoSource = nil
            }
        }
        .alert(
            "Upload failed",
            isPresented: Binding(
                get: { store.uploadError != nil },
                set: { newValue in
                    if !newValue { store.send(.view(.uploadErrorDismissed)) }
                }
            ),
            presenting: store.uploadError
        ) { _ in
            Button("OK", role: .cancel) { store.send(.view(.uploadErrorDismissed)) }
        } message: { message in
            Text(message)
        }
    }

    // MARK: Challenges tab

    @ViewBuilder
    private var challengesList: some View {
        if store.isClosedForSubmissions {
            closedForSubmissionsBanner
        }
        if store.challengesByLevel.isEmpty {
            VStack {
                Text("No challenges yet.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(store.challengesByLevel, id: \.level) { group in
                        LevelHeader(
                            level: group.level,
                            isLocked: store.state.isLevelLocked(group.level),
                            progress: store.state.progressForLevel(group.level)
                        )
                        ForEach(group.challenges) { challenge in
                            ChallengeRow(
                                challenge: challenge,
                                status: store.state.status(of: challenge),
                                isClosed: store.isClosedForSubmissions,
                                isLevelLocked: store.state.isLevelLocked(challenge.level),
                                onMarkAsDone: { store.send(.view(.markAsDoneTapped(challenge))) },
                                onSubmit: { store.send(.view(.submitForValidationTapped(challenge))) }
                            )
                        }
                    }
                }
                .padding(16)
            }
        }
    }

    private var closedForSubmissionsBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "clock.badge.exclamationmark.fill")
                .foregroundStyle(.white)
            Text("Time's up — challenges are closed.")
                .font(.subheadline.bold())
                .foregroundStyle(.white)
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.hunterRed)
        .padding(.horizontal, 16)
        .padding(.top, 8)
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

private struct LevelHeader: View {
    let level: Int
    let isLocked: Bool
    let progress: (validated: Int, total: Int, threshold: Int)

    var body: some View {
        HStack(spacing: 8) {
            Text("Level \(level)")
                .font(.headline.bold())
                .foregroundStyle(Color.onSurface)
            if isLocked {
                Text("🔒 Unlock at \(progress.validated) / \(progress.total)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(.top, 4)
    }
}

private struct ChallengeRow: View {
    let challenge: Challenge
    let status: ChallengeStatus
    let isClosed: Bool
    let isLevelLocked: Bool
    let onMarkAsDone: () -> Void
    let onSubmit: () -> Void

    @Environment(\.locale) private var locale
    private var langCode: String { locale.language.languageCode?.identifier ?? "fr" }

    private var buttonLabel: LocalizedStringKey {
        switch status {
        case .available: return "I did it!"
        case .pendingLocal: return "Submit for validation"
        case .submitting: return "Uploading…"
        case .awaitingValidation: return "⏳ Awaiting validation"
        case .validated: return "✓ Validated"
        case .rejected: return "Rejected"
        }
    }

    private var buttonFill: Color {
        switch status {
        case .available: return Color.CROrange
        case .pendingLocal: return Color.CRPink
        case .submitting: return Color.CROrange.opacity(0.6)
        case .awaitingValidation: return Color.CROrange.opacity(0.2)
        case .validated: return Color.success.opacity(0.15)
        case .rejected: return Color.gray.opacity(0.2)
        }
    }

    private var labelColor: Color {
        switch status {
        case .validated: return Color.success
        case .awaitingValidation: return Color.CROrange
        case .rejected: return Color.onSurface
        default: return Color.white
        }
    }

    private var buttonAction: () -> Void {
        switch status {
        case .available: return onMarkAsDone
        case .pendingLocal: return onSubmit
        default: return {}
        }
    }

    private var isButtonDisabled: Bool {
        if isClosed { return true }
        switch status {
        case .pendingLocal: return isLevelLocked
        case .submitting, .awaitingValidation, .validated, .rejected: return true
        case .available: return false
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    if challenge.number > 0 {
                        Text("#\(challenge.number)")
                            .font(.headline.bold())
                            .foregroundStyle(Color.CROrange)
                    }
                    Text(challenge.localizedTitle(langCode))
                        .font(.headline)
                        .bold()
                        .foregroundStyle(Color.onSurface)
                }
                Text(challenge.localizedBody(langCode))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text("\(challenge.points) pts")
                    .font(.caption)
                    .bold()
                    .foregroundStyle(Color.CROrange)
            }
            Spacer()
            Button(action: buttonAction) {
                Text(buttonLabel)
                    .font(.system(size: 13, weight: .bold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(buttonFill))
                    .foregroundStyle(labelColor)
            }
            .disabled(isButtonDisabled)
        }
        .padding(12)
        .opacity(isClosed && status != .validated ? 0.55 : 1.0)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(status == .validated ? Color.success : Color.gray.opacity(0.2), lineWidth: status == .validated ? 2 : 1)
        )
        .neonGlow(status == .validated ? Color.success : Color.clear, intensity: .medium)
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

private struct CameraPicker: UIViewControllerRepresentable {
    let onPicked: (Data?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        if UIImagePickerController.isSourceTypeAvailable(.camera) {
            picker.sourceType = .camera
            picker.cameraCaptureMode = .photo
        } else {
            picker.sourceType = .photoLibrary
        }
        picker.allowsEditing = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let onPicked: (Data?) -> Void
        init(onPicked: @escaping (Data?) -> Void) { self.onPicked = onPicked }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let image = (info[.originalImage] as? UIImage)
            picker.dismiss(animated: true) { [onPicked] in
                onPicked(image?.jpegDataResized())
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true) { [onPicked] in
                onPicked(nil)
            }
        }
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
