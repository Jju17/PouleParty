import AVKit
import ComposableArchitecture
import FirebaseFirestore
import os
import SwiftUI

private let logger = Logger(category: "ValidationQueue")

@Reducer
struct ValidationQueueFeature {

    @ObservableState
    struct State: Equatable {
        let gameId: String
        let hunterIds: [String]
        var submissions: [ChallengeSubmission] = []
        var challenges: [Challenge] = []
        var registrations: [Registration] = []
        var selected: ChallengeSubmission?
        var busyIds: Set<String> = []
        var error: String?

        func challenge(for submission: ChallengeSubmission) -> Challenge? {
            challenges.first(where: { $0.id == submission.challengeId })
        }

        func teamName(for hunterId: String) -> String {
            if let reg = registrations.first(where: { $0.userId == hunterId }),
               !reg.teamName.isEmpty {
                return reg.teamName
            }
            return "Hunter"
        }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case `internal`(Internal)
        case view(View)

        @CasePathable
        enum View {
            case onTask
            case closeTapped
            case submissionTapped(ChallengeSubmission)
            case fullscreenDismissed
            case validateTapped(ChallengeSubmission)
            case rejectTapped(ChallengeSubmission)
            case errorDismissed
        }

        @CasePathable
        enum Internal {
            case submissionsUpdated([ChallengeSubmission])
            case challengesUpdated([Challenge])
            case registrationsLoaded([Registration])
            case validateSucceeded(String)
            case validateFailed(String, String)
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

            case .view(.onTask):
                let gameId = state.gameId
                logger.info("[ValidationQueue] onTask for game \(gameId, privacy: .public)")
                return .merge(
                    .run { send in
                        for await subs in apiClient.pendingSubmissionsStream(gameId) {
                            await send(.internal(.submissionsUpdated(subs)))
                        }
                    },
                    .run { send in
                        for await challenges in apiClient.challengesStream() {
                            await send(.internal(.challengesUpdated(challenges)))
                        }
                    },
                    .run { send in
                        for await regs in apiClient.registrationsStream(gameId) {
                            await send(.internal(.registrationsLoaded(regs)))
                        }
                    }
                )

            case .view(.closeTapped):
                return .run { _ in await dismiss() }

            case let .view(.submissionTapped(submission)):
                state.selected = submission
                return .none

            case .view(.fullscreenDismissed):
                state.selected = nil
                return .none

            case .view(.errorDismissed):
                state.error = nil
                return .none

            case let .view(.validateTapped(submission)):
                return validate(state: &state, submission: submission, accept: true)

            case let .view(.rejectTapped(submission)):
                return validate(state: &state, submission: submission, accept: false)

            case let .internal(.submissionsUpdated(subs)):
                state.submissions = subs
                if let selected = state.selected, !subs.contains(where: { $0.firestoreId == selected.firestoreId }) {
                    state.selected = nil
                }
                return .none

            case let .internal(.challengesUpdated(challenges)):
                state.challenges = challenges
                return .none

            case let .internal(.registrationsLoaded(regs)):
                state.registrations = regs
                return .none

            case let .internal(.validateSucceeded(id)):
                state.busyIds.remove(id)
                state.selected = nil
                return .none

            case let .internal(.validateFailed(id, message)):
                state.busyIds.remove(id)
                state.error = message
                return .none
            }
        }
    }

    private func validate(state: inout State, submission: ChallengeSubmission, accept: Bool) -> Effect<Action> {
        guard let id = submission.firestoreId, !id.isEmpty else {
            logger.warning("[ValidationQueue] validate tap dropped — submission.firestoreId is nil. challengeId=\(submission.challengeId, privacy: .public) hunterId=\(submission.hunterId, privacy: .public)")
            return .none
        }
        if state.busyIds.contains(id) {
            logger.info("[ValidationQueue] validate tap dropped — already busy on \(id, privacy: .public)")
            return .none
        }
        state.busyIds.insert(id)
        let gameId = state.gameId
        logger.info("[ValidationQueue] validate \(id, privacy: .public) accept=\(accept)")
        return .run { send in
            do {
                try await apiClient.validateChallengeSubmission(gameId, id, accept)
                await send(.internal(.validateSucceeded(id)))
            } catch {
                logger.error("validateChallengeSubmission failed: \(error.localizedDescription)")
                await send(.internal(.validateFailed(id, error.localizedDescription)))
            }
        }
    }
}

struct ValidationQueueView: View {
    @Bindable var store: StoreOf<ValidationQueueFeature>

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Validation")
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
                    }
                }
        }
        .background(Color.surface)
        .task { store.send(.view(.onTask)) }
        .sheet(item: Binding(
            get: { store.selected },
            set: { newValue in
                if newValue == nil { store.send(.view(.fullscreenDismissed)) }
            }
        )) { submission in
            SubmissionDetailView(
                submission: submission,
                challenge: store.state.challenge(for: submission),
                teamName: store.state.teamName(for: submission.hunterId),
                isBusy: store.state.busyIds.contains(submission.firestoreId ?? ""),
                onValidate: { store.send(.view(.validateTapped(submission))) },
                onReject: { store.send(.view(.rejectTapped(submission))) },
                onClose: { store.send(.view(.fullscreenDismissed)) }
            )
        }
        .alert(
            "Validation failed",
            isPresented: Binding(
                get: { store.error != nil },
                set: { newValue in
                    if !newValue { store.send(.view(.errorDismissed)) }
                }
            ),
            presenting: store.error
        ) { _ in
            Button("OK", role: .cancel) { store.send(.view(.errorDismissed)) }
        } message: { message in
            Text(message)
        }
    }

    @ViewBuilder
    private var content: some View {
        if store.submissions.isEmpty {
            VStack(spacing: 16) {
                Image(systemName: "checkmark.seal")
                    .font(.system(size: 48))
                    .foregroundStyle(.secondary)
                Text("No submissions pending")
                    .font(.headline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(store.submissions) { submission in
                        SubmissionRow(
                            submission: submission,
                            challenge: store.state.challenge(for: submission),
                            teamName: store.state.teamName(for: submission.hunterId),
                            onTap: { store.send(.view(.submissionTapped(submission))) }
                        )
                    }
                }
                .padding(16)
            }
        }
    }
}

private struct SubmissionRow: View {
    let submission: ChallengeSubmission
    let challenge: Challenge?
    let teamName: String
    let onTap: () -> Void

    @Environment(\.locale) private var locale
    private var langCode: String { locale.language.languageCode?.identifier ?? "fr" }

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 12) {
                Group {
                    if submission.mediaType == .video {
                        ZStack {
                            Color.black
                            Image(systemName: "play.rectangle.fill")
                                .font(.system(size: 28))
                                .foregroundStyle(.white)
                        }
                    } else {
                        AsyncImage(url: URL(string: submission.mediaUrl)) { phase in
                            switch phase {
                            case .success(let image):
                                image.resizable().scaledToFill()
                            case .failure:
                                Image(systemName: "photo")
                                    .foregroundStyle(.secondary)
                            case .empty:
                                ProgressView()
                            @unknown default:
                                EmptyView()
                            }
                        }
                    }
                }
                .frame(width: 72, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(
                    RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.2), lineWidth: 1)
                )
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Image(systemName: typeIcon)
                            .foregroundStyle(typeColor)
                        Text(challenge?.localizedTitle(langCode) ?? submission.challengeId)
                            .font(.headline)
                            .foregroundStyle(Color.onSurface)
                            .lineLimit(1)
                    }
                    Text(teamName)
                        .font(.subheadline.bold())
                        .foregroundStyle(Color.CRPink)
                    if let challenge {
                        Text("\(challenge.points) pts")
                            .font(.caption)
                            .foregroundStyle(Color.CROrange)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .padding(12)
            .background(Color.surface)
            .overlay(
                RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.2), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    private var typeIcon: String {
        submission.type == .repeatable ? "arrow.triangle.2.circlepath" : "checkmark.seal"
    }

    private var typeColor: Color {
        submission.type == .repeatable ? Color.CRPink : Color.CROrange
    }
}

private struct SubmissionDetailView: View {
    let submission: ChallengeSubmission
    let challenge: Challenge?
    let teamName: String
    let isBusy: Bool
    let onValidate: () -> Void
    let onReject: () -> Void
    let onClose: () -> Void

    @Environment(\.locale) private var locale
    @State private var videoPlayer: AVPlayer?
    @State private var videoErrorMessage: String?
    private var langCode: String { locale.language.languageCode?.identifier ?? "fr" }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Group {
                    if submission.mediaType == .video,
                       let url = URL(string: submission.mediaUrl) {
                        VideoPlayerSection(
                            url: url,
                            player: $videoPlayer,
                            errorMessage: $videoErrorMessage
                        )
                    } else {
                        AsyncImage(url: URL(string: submission.mediaUrl)) { phase in
                            switch phase {
                            case .success(let image):
                                image.resizable().scaledToFit()
                            case .failure:
                                Image(systemName: "photo")
                                    .font(.system(size: 60))
                                    .foregroundStyle(.secondary)
                            case .empty:
                                ProgressView()
                            @unknown default:
                                EmptyView()
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(16)

                VStack(alignment: .leading, spacing: 8) {
                    Text(teamName)
                        .font(.title2.bold())
                        .foregroundStyle(Color.CRPink)
                    if let challenge {
                        Text(challenge.localizedTitle(langCode))
                            .font(.headline)
                        Text(challenge.localizedBody(langCode))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("\(challenge.points) pts")
                            .font(.subheadline.bold())
                            .foregroundStyle(Color.CROrange)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)

                Spacer()

                HStack(spacing: 12) {
                    Button(action: onReject) {
                        Text("Reject")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.gray.opacity(0.2))
                            .foregroundStyle(Color.onSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .disabled(isBusy)
                    Button(action: onValidate) {
                        if isBusy {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.CROrange.opacity(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        } else {
                            Text("Validate")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.CROrange)
                                .foregroundStyle(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                    }
                    .disabled(isBusy)
                }
                .padding(16)
            }
            .navigationTitle("Submission")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onClose) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }
}

/// Video player section that surfaces AVPlayer errors as a retry CTA.
/// Without this, a failed network load (common on flaky connections to
/// Firebase Storage) leaves the validator staring at a black frame.
private struct VideoPlayerSection: View {
    let url: URL
    @Binding var player: AVPlayer?
    @Binding var errorMessage: String?

    var body: some View {
        ZStack {
            if let player {
                VideoPlayer(player: player)
                    .aspectRatio(16/9, contentMode: .fit)
            } else {
                Color.black.aspectRatio(16/9, contentMode: .fit)
                ProgressView().tint(.white)
            }
            if let errorMessage {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 32))
                        .foregroundStyle(Color.CROrange)
                    Text(errorMessage)
                        .font(.subheadline)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    Button {
                        loadPlayer()
                    } label: {
                        Text("Retry")
                            .font(.headline)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .background(Color.CROrange)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }
                }
                .padding()
                .background(.black.opacity(0.7))
            }
        }
        .task(id: url) {
            loadPlayer()
            // Observe item status via KVO-bridged Combine. When the
            // player's currentItem fails, surface the error so the user
            // can retry instead of staring at black.
            guard let item = player?.currentItem else { return }
            for await status in item.publisher(for: \.status).values {
                switch status {
                case .failed:
                    let msg = item.error?.localizedDescription ?? "Could not load video."
                    await MainActor.run { errorMessage = msg }
                    return
                case .readyToPlay:
                    await MainActor.run { errorMessage = nil }
                default:
                    break
                }
            }
        }
    }

    private func loadPlayer() {
        errorMessage = nil
        let asset = AVURLAsset(url: url, options: [
            "AVURLAssetPreferPreciseDurationAndTimingKey": false,
        ])
        let item = AVPlayerItem(asset: asset)
        item.preferredForwardBufferDuration = 2
        player = AVPlayer(playerItem: item)
    }
}
