//
//  JoinFlow.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct JoinFlowFeature {

    enum Step: Equatable {
        case enteringCode
        case validating
        case codeValidated(Game)
        case validationCodeEntry(Game)
        case submittingValidationCode(Game)
        case joiningWithTeamName(Game)
        case submittingJoin(Game)
        case codeNotFound
        case gameOver
        case gameFull
        case networkError
        case gameMasterPasswordEntry(Game)
        case submittingGameMasterPassword(Game)
    }

    @ObservableState
    struct State: Equatable {
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        var code: String = ""
        var teamName: String = ""
        var step: Step = .enteringCode
        var gameMasterPassword: String = ""
        var gameMasterError: String?
        /// PP-52: registration-code buffer + last error for the paid-event join gate.
        var validationCode: String = ""
        var validationCodeError: String?

        var isCodeValid: Bool {
            let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            return trimmed.count == AppConstants.gameCodeLength
                && trimmed.allSatisfy { $0.isLetter || $0.isNumber }
        }

        var isTeamNameValid: Bool {
            let trimmed = teamName.trimmingCharacters(in: .whitespacesAndNewlines)
            return !trimmed.isEmpty && !ProfanityFilter.containsProfanity(trimmed)
        }
        /// True when a non-empty team name trips the profanity filter, so the
        /// form can show an inline hint. The Join button is gated on
        /// `isTeamNameValid`, which already excludes profane names, so the
        /// join is never submitted with one.
        var isTeamNameProfane: Bool {
            let trimmed = teamName.trimmingCharacters(in: .whitespacesAndNewlines)
            return !trimmed.isEmpty && ProfanityFilter.containsProfanity(trimmed)
        }

        var isGameMasterPasswordValid: Bool {
            gameMasterPassword.count == 4 && gameMasterPassword.allSatisfy { $0.isNumber }
        }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case codeChanged(String)
        case codeValidationFailed
        case codeValidationSucceeded(Game)
        case delegate(Delegate)
        case joinAsHunterTapped
        case submitJoinTapped
        case joinSucceeded(Game, teamName: String)
        case joinFailed(String)
        case networkErrorOccurred
        case submitValidationCodeTapped
        case validationCodeAccepted(Game)
        case validationCodeRejected(ValidationCodeResult)
        case validationCodeErrored
        case joinAsGameMasterTapped
        case submitGameMasterPasswordTapped
        case gameMasterJoinSucceeded(Game)
        case gameMasterJoinFailed(attemptsRemaining: Int, lockedUntilMs: Int?)
        case gameMasterJoinErrored(String)

        enum Delegate: Equatable {
            case joinGame(Game, hunterName: String)
            case joinedAsGameMaster(Game)
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.userClient) var userClient
    @Dependency(\.analyticsClient) var analyticsClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding(\.code):
                return .send(.codeChanged(state.code))

            case .binding:
                return .none

            case let .codeChanged(rawCode):
                // Ignore transient `.code` binding updates that fire after the
                // user already moved past code entry (e.g. autocaps re-fire or
                // focus teardown during the animated transition to the team-name
                // form). Without this gate, a single stray fire on a 6-char
                // `state.code` falls through to the "kick off validation" path
                // below and yanks the user back to `.codeValidated`.
                switch state.step {
                case .enteringCode, .validating, .codeNotFound, .gameOver, .gameFull, .networkError, .codeValidated:
                    break
                case .joiningWithTeamName, .submittingJoin,
                     .validationCodeEntry, .submittingValidationCode,
                     .gameMasterPasswordEntry, .submittingGameMasterPassword:
                    return .none
                }
                let normalized = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                if normalized.count == AppConstants.gameCodeLength,
                   normalized.allSatisfy({ $0.isLetter || $0.isNumber }) {
                    if case .validating = state.step { return .none }
                    if case let .codeValidated(game) = state.step, game.gameCode == normalized {
                        return .none
                    }
                    state.step = .validating
                    let userId = userClient.currentUserId() ?? ""
                    return .run { send in
                        do {
                            guard let game = try await apiClient.findGameByCode(normalized) else {
                                await send(.codeValidationFailed)
                                return
                            }
                            if game.isChicken(userId) {
                                await send(.codeValidationFailed)
                                return
                            }
                            await send(.codeValidationSucceeded(game))
                        } catch {
                            await send(.networkErrorOccurred)
                        }
                    }
                } else {
                    state.step = .enteringCode
                    return .none
                }

            case .codeValidationFailed:
                state.step = .codeNotFound
                return .none

            case let .codeValidationSucceeded(game):
                // A finished game can't be joined (the server rejects it too),
                // so route to a terminal "party over" message instead of
                // looping the hunter through the join form. Parity with Android.
                if game.status == .done {
                    state.step = .gameOver
                    return .none
                }
                // Pre-check capacity so an over-capacity join surfaces a
                // dedicated message instead of failing the rule generically.
                // A user already in the game (rejoin) is never blocked.
                let userId = userClient.currentUserId() ?? ""
                let isAlreadyMember = game.role(of: userId) != nil
                if !isAlreadyMember, game.hunterIds.count >= game.maxPlayers {
                    state.step = .gameFull
                    return .none
                }
                state.step = .codeValidated(game)
                if state.teamName.isEmpty {
                    state.teamName = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                }
                return .none

            case .delegate:
                return .none

            case .joinAsHunterTapped:
                guard case let .codeValidated(game) = state.step else { return .none }
                // PP-52: a game linked to a paid registration batch requires the
                // unique registration code (validated server-side) before the
                // teamName step. Free games go straight to teamName.
                if game.registrationBatchId != nil {
                    state.validationCode = ""
                    state.validationCodeError = nil
                    state.step = .validationCodeEntry(game)
                } else {
                    state.step = .joiningWithTeamName(game)
                }
                return .none

            case .submitValidationCodeTapped:
                guard case let .validationCodeEntry(game) = state.step,
                      let batchId = game.registrationBatchId
                else { return .none }
                let code = state.validationCode.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !code.isEmpty else { return .none }
                state.step = .submittingValidationCode(game)
                state.validationCodeError = nil
                return .run { send in
                    do {
                        let result = try await apiClient.validateRegistrationCode(batchId, code)
                        switch result {
                        case .valid:
                            await send(.validationCodeAccepted(game))
                        case .invalid, .alreadyUsed:
                            await send(.validationCodeRejected(result))
                        }
                    } catch {
                        await send(.validationCodeErrored)
                    }
                }

            case let .validationCodeAccepted(game):
                state.step = .joiningWithTeamName(game)
                return .none

            case let .validationCodeRejected(result):
                if case let .submittingValidationCode(game) = state.step {
                    state.step = .validationCodeEntry(game)
                }
                state.validationCodeError = result == .alreadyUsed
                    ? String(localized: "This code has already been used to join.")
                    : String(localized: "We couldn't find a registration for this code. Check the email we sent you.")
                return .none

            case .validationCodeErrored:
                state.step = .networkError
                return .none

            case .submitJoinTapped:
                guard case let .joiningWithTeamName(game) = state.step,
                      state.isTeamNameValid
                else { return .none }
                let userId = userClient.currentUserId() ?? ""
                let teamName = state.teamName.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !userId.isEmpty else { return .none }

                state.step = .submittingJoin(game)
                return .run { send in
                    do {
                        // PP-107: the server writes the role + the
                        // `/players/{uid}` team-name doc + membership index.
                        try await apiClient.joinGame(game.id, teamName)
                        await send(.joinSucceeded(game, teamName: teamName))
                    } catch {
                        await send(.joinFailed(error.localizedDescription))
                    }
                }

            case let .joinSucceeded(game, teamName):
                return .send(.delegate(.joinGame(game, hunterName: teamName)))

            case .joinFailed:
                // Recover to the team-name form (not codeValidated) so the
                // typed team name is preserved and the hunter can retry the
                // join in place. Parity with Android.
                if case let .submittingJoin(game) = state.step {
                    state.step = .joiningWithTeamName(game)
                }
                return .none

            case .networkErrorOccurred:
                state.step = .networkError
                return .none

            case .joinAsGameMasterTapped:
                guard case let .codeValidated(game) = state.step else { return .none }
                state.gameMasterPassword = ""
                state.gameMasterError = nil
                state.step = .gameMasterPasswordEntry(game)
                return .none

            case .submitGameMasterPasswordTapped:
                guard case let .gameMasterPasswordEntry(game) = state.step,
                      state.isGameMasterPasswordValid
                else { return .none }
                let password = state.gameMasterPassword
                state.step = .submittingGameMasterPassword(game)
                state.gameMasterError = nil
                let gameId = game.id
                return .run { send in
                    do {
                        let result = try await apiClient.joinAsGameMaster(gameId, password)
                        if result.success {
                            await send(.gameMasterJoinSucceeded(game))
                        } else {
                            await send(.gameMasterJoinFailed(
                                attemptsRemaining: result.attemptsRemaining,
                                lockedUntilMs: result.lockedUntilMs
                            ))
                        }
                    } catch {
                        await send(.gameMasterJoinErrored(error.localizedDescription))
                    }
                }

            case let .gameMasterJoinSucceeded(game):
                return .send(.delegate(.joinedAsGameMaster(game)))

            case let .gameMasterJoinFailed(attemptsRemaining, lockedUntilMs):
                if let lockedUntilMs {
                    let secs = max(0, (lockedUntilMs - Int(Date.now.timeIntervalSince1970 * 1000)) / 1000)
                    let mins = max(1, secs / 60)
                    state.gameMasterError = String(
                        format: String(localized: "Too many attempts. Try again in %d min."),
                        mins
                    )
                } else {
                    state.gameMasterError = String(
                        format: String(localized: "Wrong code. %d attempt(s) remaining."),
                        attemptsRemaining
                    )
                }
                state.gameMasterPassword = ""
                if case let .submittingGameMasterPassword(game) = state.step {
                    state.step = .gameMasterPasswordEntry(game)
                }
                return .none

            case let .gameMasterJoinErrored(message):
                state.gameMasterError = message
                if case let .submittingGameMasterPassword(game) = state.step {
                    state.step = .gameMasterPasswordEntry(game)
                }
                return .none
            }
        }
    }
}

// MARK: - View

struct JoinFlowView: View {
    @Bindable var store: StoreOf<JoinFlowFeature>
    @FocusState private var isCodeFieldFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(Color.onBackground.opacity(0.2))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            ZStack {
                content(for: store.step)
                    .id(stepId(store.step))
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing).combined(with: .opacity),
                        removal: .move(edge: .leading).combined(with: .opacity)
                    ))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .animation(.easeInOut(duration: 0.25), value: stepId(store.step))
        }
        .background(Color.background)
        .task {
            try? await Task.sleep(for: .milliseconds(300))
            isCodeFieldFocused = true
        }
    }

    private func stepId(_ step: JoinFlowFeature.Step) -> String {
        switch step {
        case .enteringCode, .validating, .codeNotFound, .gameOver, .gameFull, .networkError, .codeValidated:
            return "code"
        case .joiningWithTeamName, .submittingJoin:
            return "teamName"
        case .validationCodeEntry, .submittingValidationCode:
            return "validationCode"
        case .gameMasterPasswordEntry, .submittingGameMasterPassword:
            return "gmPassword"
        }
    }

    @ViewBuilder
    private func content(for step: JoinFlowFeature.Step) -> some View {
        switch step {
        case .enteringCode, .validating, .codeNotFound, .gameOver, .gameFull, .networkError, .codeValidated:
            codeEntry(step: step)
        case let .validationCodeEntry(game):
            validationCodeForm(game: game, isSubmitting: false)
        case let .submittingValidationCode(game):
            validationCodeForm(game: game, isSubmitting: true)
        case let .joiningWithTeamName(game):
            teamNameForm(game: game, isSubmitting: false)
        case let .submittingJoin(game):
            teamNameForm(game: game, isSubmitting: true)
        case let .gameMasterPasswordEntry(game):
            gameMasterPasswordForm(game: game, isSubmitting: false)
        case let .submittingGameMasterPassword(game):
            gameMasterPasswordForm(game: game, isSubmitting: true)
        }
    }

    // MARK: - Code Entry

    private func codeEntry(step: JoinFlowFeature.Step) -> some View {
        VStack(spacing: 24) {
            Spacer()
            BangerText(String(localized: "Join Game"), size: 28)
                .foregroundStyle(Color.onBackground)

            Text("Enter the game code")
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.onBackground.opacity(0.6))

            TextField("ABC123", text: $store.code)
                .font(.gameboy(size: 22))
                .multilineTextAlignment(.center)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .focused($isCodeFieldFocused)
                .padding(.vertical, 14)
                .padding(.horizontal, 24)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.onBackground.opacity(0.2), lineWidth: 1)
                )
                .padding(.horizontal, 40)

            statusMessage(for: step)

            actionButton(for: step)

            Spacer()
        }
    }

    @ViewBuilder
    private func statusMessage(for step: JoinFlowFeature.Step) -> some View {
        switch step {
        case .validating:
            ProgressView()
                .tint(Color.CROrange)
        case .codeNotFound:
            Text("No game found with this code.")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.CROrange)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        case .gameOver:
            Text("This party is already over.")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.CROrange)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        case .gameFull:
            Text("This party is full.")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.CROrange)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        case .networkError:
            Text("Network error. Please try again.")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.CROrange)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        default:
            Color.clear.frame(height: 1)
        }
    }

    @ViewBuilder
    private func actionButton(for step: JoinFlowFeature.Step) -> some View {
        let validatedGame: Game? = {
            if case let .codeValidated(game) = step { return game }
            return nil
        }()
        let isEnabled = validatedGame != nil
        if let game = validatedGame, game.hasGameMasterPassword {
            VStack(spacing: 12) {
                Button {
                    store.send(.joinAsHunterTapped)
                } label: {
                    BangerText(String(localized: "Join as Hunter"), size: 20)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 14)
                        .background(AnyShapeStyle(Color.gradientFire))
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.2), radius: 6, y: 3)
                }
                Button {
                    store.send(.joinAsGameMasterTapped)
                } label: {
                    BangerText(String(localized: "Join as GameMaster 🦅"), size: 20)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 14)
                        .background(Color.CRPink)
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.2), radius: 6, y: 3)
                }
            }
        } else {
            Button {
                store.send(.joinAsHunterTapped)
            } label: {
                BangerText(String(localized: "Join"), size: 22)
                    .foregroundStyle(isEnabled ? .white : .white.opacity(0.5))
                    .padding(.horizontal, 28)
                    .padding(.vertical, 14)
                    .background(
                        isEnabled
                            ? AnyShapeStyle(Color.gradientFire)
                            : AnyShapeStyle(Color.gray.opacity(0.3))
                    )
                    .clipShape(Capsule())
                    .shadow(color: .black.opacity(isEnabled ? 0.2 : 0), radius: 6, y: 3)
            }
            .disabled(!isEnabled)
        }
    }

    // MARK: - GameMaster password form (PP-88)

    private func gameMasterPasswordForm(game: Game, isSubmitting: Bool) -> some View {
        VStack(spacing: 20) {
            Spacer().frame(height: 8)
            BangerText(String(localized: "GameMaster 🦅"), size: 28)
                .foregroundStyle(Color.onBackground)

            Text("4-digit code")
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.onBackground.opacity(0.6))

            SecureField("", text: $store.gameMasterPassword)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .multilineTextAlignment(.center)
                .font(.system(size: 32, weight: .bold, design: .monospaced))
                .frame(maxWidth: 200)
                .padding(.vertical, 12)
                .background(Color.onBackground.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .onChange(of: store.gameMasterPassword) { _, newValue in
                    let digits = newValue.filter { $0.isNumber }.prefix(4)
                    if digits != Substring(newValue) {
                        store.gameMasterPassword = String(digits)
                    }
                }
                .disabled(isSubmitting)

            if let err = store.gameMasterError {
                Text(err)
                    .font(.gameboy(size: 9))
                    .foregroundStyle(Color.CROrange)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            Button {
                store.send(.submitGameMasterPasswordTapped)
            } label: {
                Group {
                    if isSubmitting {
                        ProgressView()
                            .tint(.white)
                    } else {
                        BangerText(String(localized: "Submit"), size: 22)
                            .foregroundStyle(.white)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 14)
                .background(
                    (store.isGameMasterPasswordValid && !isSubmitting)
                        ? AnyShapeStyle(Color.CRPink)
                        : AnyShapeStyle(Color.gray.opacity(0.3))
                )
                .clipShape(Capsule())
            }
            .disabled(!store.isGameMasterPasswordValid || isSubmitting)

            Spacer()
        }
    }

    // MARK: - Validation Code Form (PP-52 paid-event gate)

    private func validationCodeForm(game: Game, isSubmitting: Bool) -> some View {
        let canSubmit = !store.validationCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return VStack(spacing: 20) {
            Spacer().frame(height: 8)
            BangerText(String(localized: "Validate your ticket"), size: 28)
                .foregroundStyle(Color.onBackground)

            Text("This party is part of a registered event. Enter the code from your registration email to confirm your spot.")
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.onBackground.opacity(0.6))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            TextField("ABC123", text: $store.validationCode)
                .font(.gameboy(size: 22))
                .multilineTextAlignment(.center)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .padding(.vertical, 14)
                .padding(.horizontal, 24)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.onBackground.opacity(0.2), lineWidth: 1)
                )
                .padding(.horizontal, 40)

            if let error = store.validationCodeError {
                Text(error)
                    .font(.gameboy(size: 9))
                    .foregroundStyle(Color.CROrange)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            Button {
                store.send(.submitValidationCodeTapped)
            } label: {
                Group {
                    if isSubmitting {
                        ProgressView().tint(.white)
                    } else {
                        BangerText(String(localized: "Confirm"), size: 22)
                            .foregroundStyle(.white)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 14)
                .background(
                    (canSubmit && !isSubmitting)
                        ? AnyShapeStyle(Color.gradientFire)
                        : AnyShapeStyle(Color.gray.opacity(0.3))
                )
                .clipShape(Capsule())
            }
            .disabled(!canSubmit || isSubmitting)

            Spacer()
        }
    }

    // MARK: - TeamName Form (PP-90)

    private func teamNameForm(game: Game, isSubmitting: Bool) -> some View {
        return VStack(spacing: 20) {
            Spacer().frame(height: 8)
            BangerText(String(localized: "Join the hunt"), size: 28)
                .foregroundStyle(Color.onBackground)

            Text("Game \(game.gameCode)")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground.opacity(0.6))

            VStack(alignment: .leading, spacing: 8) {
                Text("Team name")
                    .font(.gameboy(size: 9))
                    .foregroundStyle(Color.onBackground.opacity(0.7))
                TextField("The Foxes", text: $store.teamName)
                    .font(.gameboy(size: 12))
                    .padding(.vertical, 12)
                    .padding(.horizontal, 16)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.surface)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.onBackground.opacity(0.2), lineWidth: 1)
                    )
                if store.isTeamNameProfane {
                    Text("Please choose a different team name.")
                        .font(.gameboy(size: 9))
                        .foregroundStyle(Color.CROrange)
                }
            }
            .padding(.horizontal, 24)

            Button {
                store.send(.submitJoinTapped)
            } label: {
                Group {
                    if isSubmitting {
                        ProgressView()
                            .tint(.white)
                    } else {
                        BangerText(String(localized: "Join"), size: 22)
                            .foregroundStyle(.white)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 14)
                .background(
                    (store.isTeamNameValid && !isSubmitting)
                        ? AnyShapeStyle(Color.gradientFire)
                        : AnyShapeStyle(Color.gray.opacity(0.3))
                )
                .clipShape(Capsule())
            }
            .disabled(!store.isTeamNameValid || isSubmitting)

            Spacer()
        }
    }
}

#Preview {
    JoinFlowView(
        store: Store(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        }
    )
}
