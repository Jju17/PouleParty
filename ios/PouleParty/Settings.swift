//
//  Settings.swift
//  PouleParty
//

import ComposableArchitecture
import os
import SafariServices
import Sharing
import SwiftUI

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

@Reducer
struct SettingsFeature {

    @ObservableState
    struct State: Equatable {
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        @Shared(.appStorage(AppConstants.prefOnboardingCompleted)) var hasCompletedOnboarding = false
        var nickname: String = ""
        var showingDeleteConfirmation = false
        var showingDeleteSuccess = false
        var showingDeleteError = false
        var showingProfanityAlert = false
        var safariURL: URL?
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case onAppear
        case nicknameChanged(String)
        case saveNickname
        case dismissProfanityAlert
        case openURL(URL)
        case dismissSafari
        case deleteDataTapped
        case confirmDeleteData
        case deleteCompleted(success: Bool)
        case dismissDeleteSuccess
        case dismissDeleteError
    }

    @Dependency(\.userClient) var userClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .onAppear:
                state.nickname = state.savedNickname
                return .none
            case let .nicknameChanged(name):
                state.nickname = String(name.prefix(AppConstants.nicknameMaxLength))
                return .none
            case .saveNickname:
                let trimmed = state.nickname.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return .none }
                if ProfanityFilter.containsProfanity(trimmed) {
                    state.showingProfanityAlert = true
                    return .none
                }
                state.$savedNickname.withLock { $0 = trimmed }
                return .none
            case .dismissProfanityAlert:
                state.showingProfanityAlert = false
                return .none
            case let .openURL(url):
                state.safariURL = url
                return .none
            case .dismissSafari:
                state.safariURL = nil
                return .none
            case .deleteDataTapped:
                state.showingDeleteConfirmation = true
                return .none
            case .confirmDeleteData:
                state.showingDeleteConfirmation = false
                return .run { send in
                    do {
                        try await userClient.deleteAccount()
                        await send(.deleteCompleted(success: true))
                    } catch {
                        Logger(subsystem: "dev.rahier.pouleparty", category: "Settings")
                            .error("Failed to delete account: \(error.localizedDescription)")
                        await send(.deleteCompleted(success: false))
                    }
                }
            case let .deleteCompleted(success):
                if success {
                    state.showingDeleteSuccess = true
                    state.$savedNickname.withLock { $0 = "" }
                    state.$hasCompletedOnboarding.withLock { $0 = false }
                    state.nickname = ""
                } else {
                    state.showingDeleteError = true
                }
                return .none
            case .dismissDeleteSuccess:
                state.showingDeleteSuccess = false
                return .none
            case .dismissDeleteError:
                state.showingDeleteError = false
                return .none
            }
        }
    }
}

struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

struct SettingsView: View {
    @Bindable var store: StoreOf<SettingsFeature>
    var onAccountDeleted: (() -> Void)? = nil
    @FocusState private var isNicknameFocused: Bool

    var body: some View {
        List {
            Section {
                HStack {
                    Label("Nickname", systemImage: "person")
                    Spacer()
                    TextField("Your nickname", text: Binding(
                        get: { store.nickname },
                        set: { store.send(.nicknameChanged($0)) }
                    ))
                    .multilineTextAlignment(.trailing)
                    .focused($isNicknameFocused)
                    .submitLabel(.done)
                    .onSubmit {
                        store.send(.saveNickname)
                    }
                }
            } footer: {
                Text("\(store.nickname.count)/\(AppConstants.nicknameMaxLength)")
            }

            Section {
                Button {
                    if let url = URL(string: "https://pouleparty.be/privacy") {
                        store.send(.openURL(url))
                    }
                } label: {
                    Label("Privacy Policy", systemImage: "hand.raised")
                }
                Button {
                    if let url = URL(string: "https://pouleparty.be/terms") {
                        store.send(.openURL(url))
                    }
                } label: {
                    Label("Terms of Use", systemImage: "doc.text")
                }
            }

            Section {
                if let mailURL = URL(string: "mailto:julien@rahier.dev") {
                    Link(destination: mailURL) {
                        Label("Contact Support", systemImage: "envelope")
                    }
                }
            }

            Section {
                Button {
                    store.send(.deleteDataTapped)
                } label: {
                    Label("Delete My Data", systemImage: "trash")
                        .foregroundStyle(.red)
                }
            } footer: {
                Text("This will delete your anonymous account and all associated data. You can continue using the app — a new anonymous account will be created automatically.")
            }

            Section {
                HStack {
                    Text("Version")
                    Spacer()
                    Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Settings")
        .onAppear {
            store.send(.onAppear)
        }
        .onChange(of: isNicknameFocused) { _, focused in
            if !focused {
                store.send(.saveNickname)
            }
        }
        .sheet(item: Binding(
            get: { store.safariURL },
            set: { _ in store.send(.dismissSafari) }
        )) { url in
            SafariView(url: url)
                .ignoresSafeArea()
        }
        .alert("Delete My Data", isPresented: $store.showingDeleteConfirmation) {
            Button("Delete", role: .destructive) {
                store.send(.confirmDeleteData)
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("This will permanently delete your anonymous account. Any active games will be abandoned. Are you sure?")
        }
        .alert("Data Deleted", isPresented: $store.showingDeleteSuccess) {
            Button("OK") {
                store.send(.dismissDeleteSuccess)
                onAccountDeleted?()
            }
        } message: {
            Text("Your account and data have been deleted.")
        }
        .alert("Error", isPresented: $store.showingDeleteError) {
            Button("OK") {
                store.send(.dismissDeleteError)
            }
        } message: {
            Text("Failed to delete your account. Please try again later.")
        }
        .alert("Inappropriate Nickname", isPresented: $store.showingProfanityAlert) {
            Button("OK") {
                store.send(.dismissProfanityAlert)
            }
        } message: {
            Text("Please choose a different nickname. This one contains inappropriate language.")
        }
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
