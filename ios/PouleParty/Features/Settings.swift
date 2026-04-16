//
//  Settings.swift
//  PouleParty
//

import ComposableArchitecture
import os
import Sharing
import SwiftUI

@Reducer
struct SettingsFeature {

    @ObservableState
    struct State: Equatable {
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        @Shared(.appStorage(AppConstants.prefOnboardingCompleted)) var hasCompletedOnboarding = false
        var showingDeleteConfirmation = false
        var currentNickname: String { savedNickname }
        var showingDeleteSuccess = false
        var showingDeleteError = false
        var showingProfanityAlert = false
        var showingEmptyNicknameAlert = false
        var myGames: [MyGame] = []
        var isLoadingGames = false
        var selectedGame: MyGame?
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case deleteCompleted(success: Bool)
        case deleteConfirmationTapped
        case deleteDataTapped
        case deleteErrorAlertDismissed
        case deleteSuccessAlertDismissed
        case emptyNicknameAlertDismissed
        case nicknameSubmitted(String)
        case profanityAlertDismissed
        case onAppear
        case myGamesLoaded([MyGame])
        case gameTapped(MyGame)
        case gameDetailDismissed
    }

    @Dependency(\.userClient) var userClient
    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case let .nicknameSubmitted(text):
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else {
                    state.showingEmptyNicknameAlert = true
                    return .none
                }
                if ProfanityFilter.containsProfanity(trimmed) {
                    state.showingProfanityAlert = true
                    return .none
                }
                state.$savedNickname.withLock { $0 = trimmed }
                return .run { [userClient] _ in
                    await userClient.saveNickname(trimmed)
                }
            case .profanityAlertDismissed:
                state.showingProfanityAlert = false
                return .none
            case .emptyNicknameAlertDismissed:
                state.showingEmptyNicknameAlert = false
                return .none
            case .deleteDataTapped:
                state.showingDeleteConfirmation = true
                return .none
            case .deleteConfirmationTapped:
                state.showingDeleteConfirmation = false
                return .run { send in
                    do {
                        try await userClient.deleteAccount()
                        await send(.deleteCompleted(success: true))
                    } catch {
                        Logger(category: "Settings")
                            .error("Failed to delete account: \(error.localizedDescription)")
                        await send(.deleteCompleted(success: false))
                    }
                }
            case let .deleteCompleted(success):
                if success {
                    state.showingDeleteSuccess = true
                    state.$savedNickname.withLock { $0 = "" }
                    state.$hasCompletedOnboarding.withLock { $0 = false }
                } else {
                    state.showingDeleteError = true
                }
                return .none
            case .deleteSuccessAlertDismissed:
                state.showingDeleteSuccess = false
                return .none
            case .deleteErrorAlertDismissed:
                state.showingDeleteError = false
                return .none
            case .onAppear:
                state.isLoadingGames = true
                return .run { [userClient, apiClient] send in
                    guard let userId = userClient.currentUserId() else { return }
                    let games = (try? await apiClient.fetchMyGames(userId)) ?? []
                    await send(.myGamesLoaded(games))
                }
            case let .myGamesLoaded(myGames):
                state.myGames = myGames
                state.isLoadingGames = false
                return .none
            case let .gameTapped(myGame):
                state.selectedGame = myGame
                return .none
            case .gameDetailDismissed:
                state.selectedGame = nil
                return .none
            }
        }
    }
}

struct SettingsView: View {
    @Bindable var store: StoreOf<SettingsFeature>
    @FocusState private var isNicknameFocused: Bool
    @State private var nicknameText = ""
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                nicknameSection
                myGamesSection
                linksSection
                dangerSection
                versionSection
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
        .background(Color.gradientBackgroundWarmth)
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.background, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .onAppear {
            nicknameText = store.currentNickname
            store.send(.onAppear)
        }
        .sheet(item: $store.selectedGame) { myGame in
            GameDetailView(myGame: myGame)
        }
        .onChange(of: isNicknameFocused) { _, focused in
            if !focused {
                store.send(.nicknameSubmitted(nicknameText))
            }
        }
        .alert("Delete My Data", isPresented: $store.showingDeleteConfirmation) {
            Button("Delete", role: .destructive) {
                store.send(.deleteConfirmationTapped)
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("This will permanently delete your anonymous account. Any active games will be abandoned. Are you sure?")
        }
        .alert("Data Deleted", isPresented: $store.showingDeleteSuccess) {
            Button("OK") {
                store.send(.deleteSuccessAlertDismissed)
            }
        } message: {
            Text("Your account and data have been deleted.")
        }
        .alert("Error", isPresented: $store.showingDeleteError) {
            Button("OK") {
                store.send(.deleteErrorAlertDismissed)
            }
        } message: {
            Text("Failed to delete your account. Please try again later.")
        }
        .alert("Inappropriate Nickname", isPresented: $store.showingProfanityAlert) {
            Button("OK") {
                store.send(.profanityAlertDismissed)
                nicknameText = store.currentNickname
            }
        } message: {
            Text("Please choose a different nickname. This one contains inappropriate language.")
        }
        .alert("Empty Nickname", isPresented: $store.showingEmptyNicknameAlert) {
            Button("OK") {
                store.send(.emptyNicknameAlertDismissed)
                nicknameText = store.currentNickname
            }
        } message: {
            Text("Your nickname cannot be empty.")
        }
    }

    // MARK: - Nickname

    private var nicknameSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Nickname", systemImage: "person")
                .font(.banger(size: 18))
                .foregroundStyle(Color.onBackground)

            TextField("Your nickname", text: $nicknameText)
                .font(.banger(size: 22))
                .foregroundStyle(Color.onBackground)
                .multilineTextAlignment(.center)
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.onBackground.opacity(0.2), lineWidth: 1)
                )
                .focused($isNicknameFocused)
                .submitLabel(.done)
                .onChange(of: nicknameText) { _, newValue in
                    if newValue.count > AppConstants.nicknameMaxLength {
                        nicknameText = String(newValue.prefix(AppConstants.nicknameMaxLength))
                    }
                }
                .onSubmit {
                    store.send(.nicknameSubmitted(nicknameText))
                }

            BangerText("\(nicknameText.count)/\(AppConstants.nicknameMaxLength)", size: 14)
                .foregroundStyle(Color.onBackground.opacity(0.4))
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .settingsCard()
    }

    // MARK: - My Games

    private var myGamesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("My Games", systemImage: "gamecontroller")
                .font(.banger(size: 18))
                .foregroundStyle(Color.onBackground)

            if store.isLoadingGames {
                HStack {
                    Spacer()
                    ProgressView()
                        .tint(Color.CROrange)
                    Spacer()
                }
                .padding(.vertical, 12)
            } else if store.myGames.isEmpty {
                Text("No games yet")
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.onBackground.opacity(0.4))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(store.myGames.enumerated()), id: \.element.id) { index, myGame in
                        if index > 0 {
                            Divider().padding(.horizontal, 14)
                        }
                        Button {
                            store.send(.gameTapped(myGame))
                        } label: {
                            GameRowView(myGame: myGame)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .settingsCard()
    }

    // MARK: - Links

    private var linksSection: some View {
        VStack(spacing: 0) {
            settingsRow(icon: "hand.raised", title: "Privacy Policy") {
                openURL(URL(string: "https://pouleparty.be/privacy")!)
            }

            Divider().padding(.horizontal, 14)

            settingsRow(icon: "doc.text", title: "Terms of Use") {
                openURL(URL(string: "https://pouleparty.be/terms")!)
            }

            Divider().padding(.horizontal, 14)

            settingsRow(icon: "envelope", title: "Contact Support") {
                if let url = URL(string: "mailto:julien@rahier.dev") {
                    openURL(url)
                }
            }
        }
        .settingsCard(padding: 0)
    }

    // MARK: - Danger zone

    private var dangerSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                store.send(.deleteDataTapped)
            } label: {
                HStack {
                    Image(systemName: "trash")
                        .font(.banger(size: 18))
                    BangerText("Delete My Data", size: 18)
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.danger.opacity(0.85))
                )
            }

            BangerText("This will delete your anonymous account and all associated data. A new anonymous account will be created automatically.", size: 13)
                .foregroundStyle(Color.onBackground.opacity(0.4))
        }
        .settingsCard()
    }

    // MARK: - Version

    private var versionSection: some View {
        VStack(spacing: 4) {
            HStack {
                BangerText("Version", size: 16)
                    .foregroundStyle(Color.onBackground)
                Spacer()
                BangerText(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—", size: 16)
                    .foregroundStyle(Color.onBackground.opacity(0.4))
            }
            HStack {
                BangerText("Build", size: 14)
                    .foregroundStyle(Color.onBackground.opacity(0.4))
                Spacer()
                BangerText(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—", size: 14)
                    .foregroundStyle(Color.onBackground.opacity(0.4))
            }
        }
        .settingsCard()
    }

    // MARK: - Helpers

    private func settingsRow(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                    .font(.banger(size: 18))
                    .frame(width: 24)
                BangerText(title, size: 18)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.onBackground.opacity(0.3))
            }
            .foregroundStyle(Color.onBackground)
            .padding(14)
        }
    }
}

// MARK: - Game Row

private struct GameRowView: View {
    let myGame: MyGame

    private var game: Game { myGame.game }

    private var title: String {
        game.name.isEmpty ? "Game \(game.gameCode)" : game.name
    }

    var body: some View {
        HStack(spacing: 12) {
            Text(game.gameMode == .followTheChicken ? "🐔" : "📍")
                .font(.system(size: 28))

            VStack(alignment: .leading, spacing: 6) {
                // Top row: name + status badge on the right
                HStack(spacing: 8) {
                    BangerText(title, size: 16)
                        .foregroundStyle(Color.onBackground)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Spacer(minLength: 4)
                    GameStatusBadge(status: game.status)
                }

                // Bottom row: role badge + start date
                HStack(spacing: 8) {
                    RoleBadge(role: myGame.role)
                    Text(game.startDate.formatted(date: .abbreviated, time: .shortened))
                        .font(.gameboy(size: 7))
                        .foregroundStyle(Color.onBackground.opacity(0.5))
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Spacer(minLength: 0)
                }
            }

            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.onBackground.opacity(0.3))
        }
        .padding(14)
    }
}

private struct RoleBadge: View {
    let role: GameRole

    var body: some View {
        HStack(spacing: 3) {
            Text(role == .chicken ? "🐔" : "🎯")
                .font(.system(size: 9))
            Text(role == .chicken ? "CREATED" : "JOINED")
                .font(.gameboy(size: 6))
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(role == .chicken ? Color.CROrange : Color.CRPink)
        .clipShape(Capsule())
        .fixedSize(horizontal: true, vertical: false)
    }
}

private struct GameStatusBadge: View {
    let status: Game.GameStatus

    var body: some View {
        Text(label)
            .font(.gameboy(size: 6))
            .foregroundStyle(.white)
            .frame(minWidth: 50)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(color)
            .clipShape(Capsule())
    }

    private var label: String {
        switch status {
        case .waiting: "Waiting"
        case .inProgress: "Live"
        case .done: "Done"
        }
    }

    private var color: Color {
        switch status {
        case .waiting: .CROrange
        case .inProgress: Color(hex: 0x16A34A)
        case .done: .onBackground.opacity(0.3)
        }
    }
}

// MARK: - Game Detail

struct GameDetailView: View {
    let myGame: MyGame
    @Environment(\.dismiss) private var dismiss
    @State private var showingLeaderboard = false

    private var game: Game { myGame.game }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    headerSection
                    infoSection
                    playersSection
                    timingSection
                    zoneSection
                }
                .padding(20)
            }
            .background(Color.gradientBackgroundWarmth)
            .navigationTitle(game.name.isEmpty ? "Game \(game.gameCode)" : game.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(isPresented: $showingLeaderboard) {
                GameLeaderboardSheet(game: game)
            }
        }
    }

    private var headerSection: some View {
        VStack(spacing: 12) {
            Text(game.gameMode == .followTheChicken ? "🐔" : "📍")
                .font(.system(size: 48))
            BangerText(game.gameMode.title, size: 22)
                .foregroundStyle(Color.onBackground)
            HStack(spacing: 8) {
                RoleBadge(role: myGame.role)
                GameStatusBadge(status: game.status)
            }

            if game.status == .done {
                Button {
                    showingLeaderboard = true
                } label: {
                    HStack(spacing: 6) {
                        Text("🏆")
                            .font(.system(size: 18))
                        BangerText("View Leaderboard", size: 18)
                            .foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(LinearGradient(
                                colors: [.CROrange, .CRPink],
                                startPoint: .leading,
                                endPoint: .trailing
                            ))
                    )
                }
                .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .settingsCard()
    }

    private var infoSection: some View {
        VStack(spacing: 8) {
            detailRow("Game Code", value: game.gameCode)
            detailRow("Found Code", value: game.foundCode)
            detailRow("Pricing", value: game.pricing.model.title)
            if game.isPaid {
                if game.pricing.pricePerPlayer > 0 {
                    detailRow("Price/Player", value: "\(game.pricing.pricePerPlayer / 100)€")
                }
                if game.pricing.deposit > 0 {
                    detailRow("Deposit", value: "\(game.pricing.deposit / 100)€")
                }
            }
        }
        .settingsCard()
    }

    private var playersSection: some View {
        VStack(spacing: 8) {
            detailRow("Max Players", value: "\(game.maxPlayers)")
            detailRow("Hunters Joined", value: "\(game.hunterIds.count)")
            detailRow("Winners", value: "\(game.winners.count)")
            if game.chickenCanSeeHunters {
                detailRow("Chicken Sees Hunters", value: "Yes")
            }
        }
        .settingsCard()
    }

    private var timingSection: some View {
        VStack(spacing: 8) {
            detailRow("Start", value: game.startDate.formatted(date: .abbreviated, time: .shortened))
            detailRow("End", value: game.endDate.formatted(date: .abbreviated, time: .shortened))
            if game.timing.headStartMinutes > 0 {
                detailRow("Head Start", value: "\(Int(game.timing.headStartMinutes)) min")
            }
            detailRow("Power-ups", value: game.powerUps.enabled ? "On" : "Off")
        }
        .settingsCard()
    }

    private var zoneSection: some View {
        VStack(spacing: 8) {
            detailRow("Initial Radius", value: "\(Int(game.zone.radius))m")
            detailRow("Shrink Interval", value: "\(Int(game.zone.shrinkIntervalMinutes)) min")
            detailRow("Shrink Amount", value: "\(Int(game.zone.shrinkMetersPerUpdate))m")
        }
        .settingsCard()
    }

    private func detailRow(_ label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.gameboy(size: 8))
                .foregroundStyle(Color.onBackground.opacity(0.6))
            Spacer()
            BangerText(value, size: 16)
                .foregroundStyle(Color.onBackground)
        }
    }
}

// MARK: - Game Leaderboard Sheet (opened from GameDetailView for finished games)

struct GameLeaderboardSheet: View {
    let game: Game

    @Environment(\.dismiss) private var dismiss
    @Dependency(\.userClient) var userClient

    /// Builds entries from winners only (no registrations → no network fetch).
    /// Uses the same `LeaderboardEntry` model as VictoryView for a shared rendering path.
    private var entries: [LeaderboardEntry] {
        buildLeaderboardEntries(
            game: game,
            registrations: [],
            currentUserId: userClient.currentUserId() ?? ""
        )
    }

    private var hasWinners: Bool { !game.winners.isEmpty }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.gradientBackgroundWarmth.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {
                        Text("🏆")
                            .font(.system(size: 56))
                            .padding(.top, 8)

                        BangerText(game.name.isEmpty ? "Game \(game.gameCode)" : game.name, size: 22)
                            .foregroundStyle(Color.onBackground)

                        Text("Final results")
                            .font(.gameboy(size: 10))
                            .foregroundStyle(Color.onBackground.opacity(0.5))

                        if !hasWinners {
                            VStack(spacing: 8) {
                                Text("🐔").font(.system(size: 48))
                                BangerText("The Chicken survived!", size: 22)
                                    .foregroundStyle(Color.onBackground.opacity(0.7))
                                Text("No hunter found the chicken in this game")
                                    .font(.gameboy(size: 9))
                                    .foregroundStyle(Color.onBackground.opacity(0.5))
                                    .multilineTextAlignment(.center)
                            }
                            .padding(.vertical, 40)
                        } else {
                            LeaderboardContentView(entries: entries, hunterStartDate: game.hunterStartDate)
                        }
                    }
                    .padding(20)
                }
            }
            .navigationTitle("Leaderboard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Card modifier

private struct SettingsCardModifier: ViewModifier {
    var padding: CGFloat

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.surface)
            )
            .shadow(color: .black.opacity(0.15), radius: 2, y: 1)
    }
}

private extension View {
    func settingsCard(padding: CGFloat = 16) -> some View {
        modifier(SettingsCardModifier(padding: padding))
    }
}

#Preview {
    NavigationStack {
        SettingsView(
            store: Store(initialState: SettingsFeature.State()) {
                SettingsFeature()
            }
        )
    }
}
