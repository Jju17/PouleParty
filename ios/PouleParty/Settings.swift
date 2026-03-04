//
//  Settings.swift
//  PouleParty
//

import ComposableArchitecture
import FirebaseAuth
import os
import SwiftUI

@Reducer
struct SettingsFeature {

    @ObservableState
    struct State: Equatable {
        var showingDeleteConfirmation = false
        var showingDeleteSuccess = false
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case deleteDataTapped
        case confirmDeleteData
        case deleteCompleted
        case dismissDeleteSuccess
    }

    @Dependency(\.authClient) var authClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .deleteDataTapped:
                state.showingDeleteConfirmation = true
                return .none
            case .confirmDeleteData:
                state.showingDeleteConfirmation = false
                return .run { send in
                    do {
                        try await Auth.auth().currentUser?.delete()
                    } catch {
                        Logger(subsystem: "dev.rahier.pouleparty", category: "Settings")
                            .error("Failed to delete account: \(error.localizedDescription)")
                    }
                    await send(.deleteCompleted)
                }
            case .deleteCompleted:
                state.showingDeleteSuccess = true
                return .none
            case .dismissDeleteSuccess:
                state.showingDeleteSuccess = false
                return .none
            }
        }
    }
}

struct SettingsView: View {
    @Bindable var store: StoreOf<SettingsFeature>

    var body: some View {
        List {
            Section {
                Link(destination: URL(string: "https://pouleparty.be/privacy")!) {
                    Label("Privacy Policy", systemImage: "hand.raised")
                }
                Link(destination: URL(string: "https://pouleparty.be/terms")!) {
                    Label("Terms of Use", systemImage: "doc.text")
                }
            }

            Section {
                Link(destination: URL(string: "mailto:julien@rahier.dev")!) {
                    Label("Contact Support", systemImage: "envelope")
                }
            }

            Section {
                Button(role: .destructive) {
                    store.send(.deleteDataTapped)
                } label: {
                    Label("Delete My Data", systemImage: "trash")
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
            }
        } message: {
            Text("Your account and data have been deleted.")
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
