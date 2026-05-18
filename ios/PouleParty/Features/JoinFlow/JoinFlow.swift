//
//  JoinFlow.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI
import os

@Reducer
struct JoinFlowFeature {

    enum Step: Equatable {
        case enteringCode
        case validating
        /// PP-90: after the code resolves, every hunter goes through
        /// the teamName form before joining. No more "registration
        /// required vs open" branching — teamName is collected always.
        case codeValidated(Game)
        /// PP-52: the resolved game has `registrationBatchId`, so we
        /// gate the join on the validation code the hunter received
        /// by email before letting them onto the teamName form.
        case validationCodeEntry(Game)
        /// PP-52: `validateRegistrationCode` call in flight.
        case submittingValidationCode(Game)
        case joiningWithTeamName(Game)
        case submittingJoin(Game)
        case codeNotFound
        case networkError
        /// PP-88: chicken set a GM password on this game; the user
        /// picked "Rejoindre comme GameMaster". Collect the 4-digit
        /// code and call `joinAsGameMaster`.
        case gameMasterPasswordEntry(Game)
        /// PP-88: `joinAsGameMaster` in flight.
        case submittingGameMasterPassword(Game)
        /// PP-52 deeplink — `lookupGameByValidationCode` call in
        /// flight (spinner). The pre-filled `validationCode` is what
        /// triggered the lookup.
        case resolvingDeeplink(code: String)
        /// PP-52 deeplink — inscription is valid but the in-app Game
        /// hasn't been created yet (D-Day day-of, chicken hasn't
        /// configured the party). Friendly "come back later" screen.
        case deeplinkGameNotYetReady(code: String)
        /// PP-52 deeplink — no `eventRegistration` matches the code.
        /// Likely a typo or a stale link.
        case deeplinkInvalidCode
    }

    @ObservableState
    struct State: Equatable {
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        var code: String = ""
        var teamName: String = ""
        var step: Step = .enteringCode
        /// PP-88: 4-digit GM password buffer. Cleared on every new
        /// step transition so a stale entry can't leak across games.
        var gameMasterPassword: String = ""
        /// PP-88: last error message from `joinAsGameMaster` (wrong
        /// code → attempts remaining ; lock → timer).
        var gameMasterError: String?
        /// PP-52: 6-char alphanum validation code typed by the hunter
        /// (or pre-filled from the email deeplink `?code=…`).
        var validationCode: String = ""
        /// PP-52: localized error string when the entered code isn't
        /// a paid match for the game's `registrationBatchId`.
        var validationCodeError: String?
        /// PP-52: when the app is opened via a deeplink that carries
        /// a validation code, we stash it here so the JoinFlow can
        /// drop it into `validationCode` the moment a game with a
        /// matching `registrationBatchId` is resolved.
        var pendingValidationCode: String?

        var isCodeValid: Bool {
            let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            return trimmed.count == AppConstants.gameCodeLength
                && trimmed.allSatisfy { $0.isLetter || $0.isNumber }
        }

        var isTeamNameValid: Bool {
            !teamName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }

        var isGameMasterPasswordValid: Bool {
            gameMasterPassword.count == 4 && gameMasterPassword.allSatisfy { $0.isNumber }
        }

        var isValidationCodeValid: Bool {
            let trimmed = validationCode.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.count >= 4 && trimmed.allSatisfy { $0.isLetter || $0.isNumber }
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
        // PP-88
        case joinAsGameMasterTapped
        case submitGameMasterPasswordTapped
        case gameMasterJoinSucceeded(Game)
        case gameMasterJoinFailed(attemptsRemaining: Int, lockedUntilMs: Int?)
        case gameMasterJoinErrored(String)
        // PP-52
        case submitValidationCodeTapped
        case validationCodeAccepted(Game)
        case validationCodeRejected
        case validationCodeErrored(String)
        case deeplinkValidationCodeReceived(String)
        /// PP-52 — `lookupGameByValidationCode` returned a result.
        case deeplinkLookupCompleted(ValidationCodeLookupResult)
        /// PP-52 — `lookupGameByValidationCode` threw (network /
        /// permission). Falls back to the generic `.networkError`
        /// step so the user can retry.
        case deeplinkLookupErrored(String)
        /// PP-52 — user tapped "OK" on either the
        /// `deeplinkGameNotYetReady` or `deeplinkInvalidCode` screen.
        /// Resets the JoinFlow so they can keep using it (manual
        /// gameCode entry still works with the pre-filled code).
        case deeplinkDismissed

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
                let normalized = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                if normalized.count == AppConstants.gameCodeLength,
                   normalized.allSatisfy({ $0.isLetter || $0.isNumber }) {
                    // Skip if we're already validating or have already validated this code
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
                            // Block the chicken from joining their own game as
                            // a hunter: they'd end up in both `chickenId` and
                            // `hunterIds` and break the map (PP-26 — the
                            // chicken may now be any designated user, not
                            // just the creator).
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
                state.step = .codeValidated(game)
                // Pre-fill teamName with the user's saved nickname so
                // a returning player doesn't have to retype.
                if state.teamName.isEmpty {
                    state.teamName = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                }
                // PP-52: if the user arrived via a deeplink that
                // carried a validation code, drop it into the
                // validation field now (only when the game actually
                // requires one). Mismatched batchId is caught by the
                // submit action just like a manually-typed wrong code.
                if let pending = state.pendingValidationCode,
                   game.registrationBatchId != nil,
                   state.validationCode.isEmpty {
                    state.validationCode = pending
                }
                state.pendingValidationCode = nil
                return .none

            case .delegate:
                return .none

            case .joinAsHunterTapped:
                guard case let .codeValidated(game) = state.step else { return .none }
                // PP-52: paid-batch games route through the validation
                // code step first; everyone else lands directly on the
                // teamName form (PP-90).
                if game.registrationBatchId?.isEmpty == false {
                    state.validationCodeError = nil
                    state.step = .validationCodeEntry(game)
                } else {
                    state.step = .joiningWithTeamName(game)
                }
                return .none

            case .submitJoinTapped:
                guard case let .joiningWithTeamName(game) = state.step,
                      state.isTeamNameValid
                else { return .none }
                let userId = userClient.currentUserId() ?? ""
                let teamName = state.teamName.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !userId.isEmpty else { return .none }

                state.step = .submittingJoin(game)
                let registration = Registration(userId: userId, teamName: teamName)
                return .run { send in
                    do {
                        // PP-90: create the registration doc *first* so
                        // the teamName is visible to the chicken / GM
                        // by the time the user lands in `hunterIds`.
                        try await apiClient.createRegistration(game.id, registration)
                        await send(.joinSucceeded(game, teamName: teamName))
                    } catch {
                        await send(.joinFailed(error.localizedDescription))
                    }
                }

            case let .joinSucceeded(game, teamName):
                return .send(.delegate(.joinGame(game, hunterName: teamName)))

            case .joinFailed:
                // For now we fall back to networkError UI; the user can
                // retry by tapping the join button again from
                // `codeValidated`.
                if case let .submittingJoin(game) = state.step {
                    state.step = .codeValidated(game)
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
                // CRIT-8 / HIGH-19 (audit 2026-05-17): routed through
                // `String(localized:)` so FR / NL users see localized copy
                // instead of hardcoded French interpolation.
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

            // MARK: - PP-52 validation code

            case .submitValidationCodeTapped:
                guard case let .validationCodeEntry(game) = state.step,
                      state.isValidationCodeValid,
                      let batchId = game.registrationBatchId,
                      !batchId.isEmpty
                else { return .none }
                let code = state.validationCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                state.validationCodeError = nil
                state.step = .submittingValidationCode(game)
                return .run { send in
                    do {
                        let ok = try await apiClient.validateRegistrationCode(batchId, code)
                        if ok {
                            await send(.validationCodeAccepted(game))
                        } else {
                            await send(.validationCodeRejected)
                        }
                    } catch {
                        await send(.validationCodeErrored(error.localizedDescription))
                    }
                }

            case let .validationCodeAccepted(game):
                state.step = .joiningWithTeamName(game)
                return .none

            case .validationCodeRejected:
                state.validationCodeError = String(localized: "Invalid validation code. Check the email we sent you.")
                if case let .submittingValidationCode(game) = state.step {
                    state.step = .validationCodeEntry(game)
                }
                return .none

            case let .validationCodeErrored(message):
                state.validationCodeError = message
                if case let .submittingValidationCode(game) = state.step {
                    state.step = .validationCodeEntry(game)
                }
                return .none

            case let .deeplinkValidationCodeReceived(code):
                let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                guard !normalized.isEmpty else { return .none }
                Logger(category: "PP-52-Deeplink").notice("[JoinFlowFeature] lookup starting for code=\(normalized, privacy: .public)")
                state.validationCode = normalized
                state.pendingValidationCode = nil
                state.validationCodeError = nil
                state.step = .resolvingDeeplink(code: normalized)
                return .run { send in
                    do {
                        let result = try await apiClient.lookupGameByValidationCode(normalized)
                        await send(.deeplinkLookupCompleted(result))
                    } catch {
                        await send(.deeplinkLookupErrored(error.localizedDescription))
                    }
                }

            case let .deeplinkLookupCompleted(result):
                let log = Logger(category: "PP-52-Deeplink")
                switch result {
                case let .gameReady(game):
                    log.notice("[JoinFlowFeature] lookup result: gameReady id=\(game.id, privacy: .public)")
                    // Server-side validation already done by the
                    // lookup query — skip `validationCodeEntry` and
                    // drop the user straight on the teamName form.
                    if state.teamName.isEmpty {
                        state.teamName = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    }
                    state.step = .joiningWithTeamName(game)
                case let .gameNotYetCreated(batchId):
                    log.notice("[JoinFlowFeature] lookup result: gameNotYetCreated batchId=\(batchId, privacy: .public)")
                    state.step = .deeplinkGameNotYetReady(code: state.validationCode)
                case .invalidCode:
                    log.notice("[JoinFlowFeature] lookup result: invalidCode")
                    state.step = .deeplinkInvalidCode
                }
                return .none

            case let .deeplinkLookupErrored(message):
                Logger(category: "PP-52-Deeplink").error("[JoinFlowFeature] lookup error: \(message, privacy: .public)")
                state.step = .networkError
                return .none

            case .deeplinkDismissed:
                // Reset to the manual code-entry screen but keep the
                // validation code stashed in case the user types a
                // gameCode immediately and the game DOES require a
                // matching code (manual flow takes over from there).
                state.step = .enteringCode
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
        case .enteringCode, .validating, .codeNotFound, .networkError, .codeValidated:
            return "code"
        case .validationCodeEntry, .submittingValidationCode:
            return "validation"
        case .joiningWithTeamName, .submittingJoin:
            return "teamName"
        case .gameMasterPasswordEntry, .submittingGameMasterPassword:
            return "gmPassword"
        case .resolvingDeeplink:
            return "deeplinkResolving"
        case .deeplinkGameNotYetReady:
            return "deeplinkAwaiting"
        case .deeplinkInvalidCode:
            return "deeplinkInvalid"
        }
    }

    @ViewBuilder
    private func content(for step: JoinFlowFeature.Step) -> some View {
        switch step {
        case .enteringCode, .validating, .codeNotFound, .networkError, .codeValidated:
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
        case .resolvingDeeplink:
            deeplinkResolvingView
        case let .deeplinkGameNotYetReady(code):
            deeplinkGameNotYetReadyView(code: code)
        case .deeplinkInvalidCode:
            deeplinkInvalidCodeView
        }
    }

    // MARK: - PP-52 deeplink screens

    private var deeplinkResolvingView: some View {
        VStack(spacing: 16) {
            Spacer()
            ProgressView()
                .tint(Color.CROrange)
                .scaleEffect(1.5)
            Text(String(localized: "Checking your code…"))
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.onBackground.opacity(0.6))
            Spacer()
        }
    }

    private func deeplinkGameNotYetReadyView(code: String) -> some View {
        VStack(spacing: 20) {
            Spacer()
            Text("🐔")
                .font(.system(size: 56))

            BangerText(String(localized: "Party not open yet"), size: 26)
                .foregroundStyle(Color.onBackground)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Text(String(localized: "Your validation code is saved. The party hasn't been set up yet — come back on June 6 with the game code we'll announce on site!"))
                .font(.system(size: 14))
                .foregroundStyle(Color.onBackground.opacity(0.75))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            VStack(spacing: 6) {
                Text(String(localized: "YOUR VALIDATION CODE"))
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.onBackground.opacity(0.5))
                Text(code)
                    .font(.gameboy(size: 26))
                    .foregroundStyle(Color.CRPink)
                    .tracking(6)
            }
            .padding(.vertical, 16)
            .padding(.horizontal, 28)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.CROrange.opacity(0.12))
            )

            Button {
                store.send(.deeplinkDismissed)
            } label: {
                BangerText(String(localized: "OK"), size: 22)
                    .foregroundStyle(.black)
                    .padding(.horizontal, 40)
                    .padding(.vertical, 14)
                    .background(AnyShapeStyle(Color.gradientFire))
                    .clipShape(Capsule())
            }

            Spacer()
        }
    }

    private var deeplinkInvalidCodeView: some View {
        VStack(spacing: 20) {
            Spacer()
            Text("⚠️")
                .font(.system(size: 56))

            BangerText(String(localized: "Invalid code"), size: 26)
                .foregroundStyle(Color.onBackground)

            Text(String(localized: "We couldn't find any registration matching this code. Double-check the email we sent you, or contact julien@rahier.dev."))
                .font(.system(size: 14))
                .foregroundStyle(Color.onBackground.opacity(0.75))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button {
                store.send(.deeplinkDismissed)
            } label: {
                BangerText(String(localized: "Close"), size: 22)
                    .foregroundStyle(.black)
                    .padding(.horizontal, 40)
                    .padding(.vertical, 14)
                    .background(AnyShapeStyle(Color.gradientFire))
                    .clipShape(Capsule())
            }

            Spacer()
        }
    }

    // MARK: - PP-52 validation code form

    private func validationCodeForm(game: Game, isSubmitting: Bool) -> some View {
        VStack(spacing: 20) {
            Spacer().frame(height: 8)
            BangerText(String(localized: "Validation code"), size: 28)
                .foregroundStyle(Color.onBackground)

            Text(String(localized: "Code from your email"))
                .font(.gameboy(size: 9))
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
                .disabled(isSubmitting)

            if let err = store.validationCodeError {
                Text(err)
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
                        ProgressView()
                            .tint(.black)
                    } else {
                        BangerText(String(localized: "Submit"), size: 22)
                            .foregroundStyle(.black)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 14)
                .background(
                    (store.isValidationCodeValid && !isSubmitting)
                        ? AnyShapeStyle(Color.gradientFire)
                        : AnyShapeStyle(Color.gray.opacity(0.3))
                )
                .clipShape(Capsule())
            }
            .disabled(!store.isValidationCodeValid || isSubmitting)

            // Show the game code as a quiet reminder so the user
            // knows which game they're confirming, without competing
            // with the validation-code input visually.
            Text("Game \(game.gameCode)")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground.opacity(0.5))
                .padding(.top, 8)

            Spacer()
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
            // PP-88: chicken enabled GM role on this game. Offer the
            // two roles side by side so a hunter and an arbitre can
            // both land on the same screen and pick.
            VStack(spacing: 12) {
                Button {
                    store.send(.joinAsHunterTapped)
                } label: {
                    BangerText(String(localized: "Join as Hunter"), size: 20)
                        .foregroundStyle(.black)
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
                    .foregroundStyle(isEnabled ? .black : .black.opacity(0.4))
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

            Text("Code à 4 chiffres")
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
            }
            .padding(.horizontal, 24)

            Button {
                store.send(.submitJoinTapped)
            } label: {
                Group {
                    if isSubmitting {
                        ProgressView()
                            .tint(.black)
                    } else {
                        BangerText(String(localized: "Join"), size: 22)
                            .foregroundStyle(.black)
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
