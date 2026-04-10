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
        case codeValidated(Game, alreadyRegistered: Bool)
        case registering(Game)
        case submittingRegistration(Game)
        case codeNotFound
        case networkError
    }

    @ObservableState
    struct State: Equatable {
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        var code: String = ""
        var teamName: String = ""
        var step: Step = .enteringCode

        var isCodeValid: Bool {
            let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            return trimmed.count == AppConstants.gameCodeLength
                && trimmed.allSatisfy { $0.isLetter || $0.isNumber }
        }

        var isTeamNameValid: Bool {
            !teamName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case codeChanged(String)
        case codeValidationFailed
        case codeValidationSucceeded(Game, alreadyRegistered: Bool)
        case delegate(Delegate)
        case joinTapped
        case networkErrorOccurred
        case registerTapped
        case registrationSubmitted(Game, teamName: String)
        case submitRegistrationTapped

        enum Delegate: Equatable {
            case joinGame(Game, hunterName: String)
            case registered(Game, teamName: String)
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.userClient) var userClient

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
                    if case let .codeValidated(game, _) = state.step, game.gameCode == normalized {
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
                            if game.requiresRegistration {
                                let registration = try await apiClient.findRegistration(game.id, userId)
                                await send(.codeValidationSucceeded(game, alreadyRegistered: registration != nil))
                            } else {
                                await send(.codeValidationSucceeded(game, alreadyRegistered: true))
                            }
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

            case let .codeValidationSucceeded(game, alreadyRegistered):
                state.step = .codeValidated(game, alreadyRegistered: alreadyRegistered)
                return .none

            case .delegate:
                return .none

            case .joinTapped:
                guard case let .codeValidated(game, alreadyRegistered) = state.step,
                      alreadyRegistered || !game.requiresRegistration
                else { return .none }
                let nickname = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                let finalName = nickname.isEmpty ? "Hunter" : nickname
                return .send(.delegate(.joinGame(game, hunterName: finalName)))

            case .networkErrorOccurred:
                state.step = .networkError
                return .none

            case .registerTapped:
                guard case let .codeValidated(game, _) = state.step else { return .none }
                state.step = .registering(game)
                return .none

            case let .registrationSubmitted(game, teamName):
                return .send(.delegate(.registered(game, teamName: teamName)))

            case .submitRegistrationTapped:
                guard case let .registering(game) = state.step,
                      state.isTeamNameValid
                else { return .none }
                let userId = userClient.currentUserId() ?? ""
                let teamName = state.teamName.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !userId.isEmpty else { return .none }
                state.step = .submittingRegistration(game)
                let registration = Registration(userId: userId, teamName: teamName, paid: false)
                return .run { send in
                    do {
                        try await apiClient.createRegistration(game.id, registration)
                        await send(.registrationSubmitted(game, teamName: teamName))
                    } catch {
                        await send(.networkErrorOccurred)
                    }
                }
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
        case .registering, .submittingRegistration:
            return "register"
        }
    }

    @ViewBuilder
    private func content(for step: JoinFlowFeature.Step) -> some View {
        switch step {
        case .enteringCode, .validating, .codeNotFound, .networkError, .codeValidated:
            codeEntry(step: step)
        case let .registering(game), let .submittingRegistration(game):
            registrationForm(game: game, isSubmitting: { if case .submittingRegistration = step { return true } else { return false } }())
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

    private func actionButton(for step: JoinFlowFeature.Step) -> some View {
        let needsRegister: Bool = {
            if case let .codeValidated(game, alreadyRegistered) = step {
                return game.requiresRegistration && !alreadyRegistered
            }
            return false
        }()
        let isEnabled: Bool = {
            if case .codeValidated = step { return true }
            return false
        }()
        let title = needsRegister ? String(localized: "Register") : String(localized: "Join")
        return Button {
            if needsRegister {
                store.send(.registerTapped)
            } else {
                store.send(.joinTapped)
            }
        } label: {
            BangerText(title, size: 22)
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

    // MARK: - Registration Form

    private func registrationForm(game: Game, isSubmitting: Bool) -> some View {
        let isDeposit = game.pricingModel == .deposit
        return VStack(spacing: 20) {
            Spacer().frame(height: 8)
            BangerText(String(localized: "Register"), size: 28)
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

            if isDeposit {
                Text("Hunter payment will be available soon.")
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.CROrange)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            Button {
                store.send(.submitRegistrationTapped)
            } label: {
                Group {
                    if isSubmitting {
                        ProgressView()
                            .tint(.black)
                    } else {
                        BangerText(isDeposit ? String(localized: "Pay") : String(localized: "Sign up"), size: 22)
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
