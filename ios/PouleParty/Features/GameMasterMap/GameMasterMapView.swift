//
//  GameMasterMapView.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct GameMasterMapView: View {
    @Bindable var store: StoreOf<GameMasterMapFeature>

    var body: some View {
        GameMasterMapContent(store: store)
            .safeAreaInset(edge: .top) {
                MapTopBar(
                    title: "GameMaster 🦅",
                    subtitle: "Arbitre — \(store.hunterAnnotations.count) hunters",
                    gradient: LinearGradient(colors: [.CRPink, .CROrange], startPoint: .leading, endPoint: .trailing),
                    onInfoTapped: { store.send(.view(.infoButtonTapped)) }
                )
            }
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 12) {
                    Button {
                        store.send(.view(.huntersDrawerTapped))
                    } label: {
                        Label("Hunters (\(store.hunterAnnotations.count))", systemImage: "person.3.fill")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.CROrange)
                            .foregroundStyle(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    if store.isGameOver {
                        Button {
                            store.send(.view(.leaderboardTapped))
                        } label: {
                            Label("Leaderboard", systemImage: "trophy.fill")
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(Color.CROrange)
                                .foregroundStyle(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                    } else {
                        ZStack(alignment: .topTrailing) {
                            Button {
                                store.send(.view(.validationQueueTapped))
                            } label: {
                                Label("Validate", systemImage: "checkmark.seal.fill")
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 12)
                                    .background(Color.CRPink)
                                    .foregroundStyle(Color.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                            if store.pendingSubmissionsCount > 0 {
                                Text("\(store.pendingSubmissionsCount)")
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.hunterRed)
                                    .foregroundStyle(Color.white)
                                    .clipShape(Capsule())
                                    .offset(x: 6, y: -6)
                            }
                        }
                    }
                    Button {
                        store.send(.view(.leaveGameTapped))
                    } label: {
                        Image(systemName: "xmark")
                            .padding(12)
                            .background(Color.white.opacity(0.2))
                            .foregroundStyle(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
            .alert(
                $store.scope(
                    state: \.destination?.alert,
                    action: \.destination.alert
                )
            )
            .sheet(isPresented: Binding(
                get: { store.showLeaderboard },
                set: { if !$0 { store.send(.view(.leaderboardDismissed)) } }
            )) {
                GameLeaderboardSheet(game: store.game)
            }
            .sheet(item: $store.scope(state: \.validationQueue, action: \.validationQueue)) { vqStore in
                ValidationQueueView(store: vqStore)
            }
            .task {
                store.send(.view(.onTask))
            }
            .idleTimerDisabled()
            .sheet(isPresented: Binding(
                get: { store.showHuntersDrawer },
                set: { if !$0 { store.send(.view(.huntersDrawerDismissed)) } }
            )) {
                NavigationStack {
                    GameMasterHuntersListView(
                        game: store.game,
                        hunterAnnotations: store.hunterAnnotations,
                        registrations: store.registrations,
                        currentChickenId: store.game.chickenId,
                        canDesignate: store.game.status == .waiting,
                        onDesignateTapped: { reg in
                            store.send(.view(.designateHunterTapped(reg)))
                        }
                    )
                    .toolbar {
                        ToolbarItem {
                            Button {
                                store.send(.view(.huntersDrawerDismissed))
                            } label: {
                                Image(systemName: "xmark")
                            }
                        }
                    }
                }
                .presentationDetents([.medium, .large])
            }
            .alert(
                "Designate the chicken",
                isPresented: Binding(
                    get: { store.pendingChickenDesignation != nil },
                    set: { if !$0 { store.send(.view(.designateCancelTapped)) } }
                ),
                presenting: store.pendingChickenDesignation
            ) { reg in
                Button("Designate \(reg.teamName)", role: .destructive) {
                    store.send(.view(.designateConfirmTapped))
                }
                Button("Cancel", role: .cancel) {
                    store.send(.view(.designateCancelTapped))
                }
            } message: { reg in
                Text("\(reg.teamName) will become the chicken. The current chicken will lose this role.")
            }
            .alert(
                "Error",
                isPresented: Binding(
                    get: { store.designationError != nil },
                    set: { if !$0 { store.send(.view(.designationErrorDismissed)) } }
                ),
                presenting: store.designationError
            ) { _ in
                Button("OK", role: .cancel) {
                    store.send(.view(.designationErrorDismissed))
                }
            } message: { message in
                Text(message)
            }
            .sheet(isPresented: Binding(
                get: { store.showGameInfo },
                set: { if !$0 { store.send(.view(.gameInfoDismissed)) } }
            )) {
                NavigationStack {
                    GameInfoView(game: store.game)
                        .toolbar {
                            ToolbarItem {
                                Button {
                                    store.send(.view(.gameInfoDismissed))
                                } label: {
                                    Image(systemName: "xmark")
                                }
                            }
                        }
                }
            }
            .overlay {
                if store.game.status == .readyToLaunch {
                    ReadyToLaunchOverlay(
                        role: .launcher,
                        isLaunching: store.isLaunching,
                        errorMessage: store.launchError,
                        onLaunchTapped: { store.send(.view(.launchTapped)) },
                        onErrorDismissed: { store.send(.view(.launchErrorDismissed)) }
                    )
                }
            }
    }
}

private struct GameMasterHuntersListView: View {
    let game: Game
    let hunterAnnotations: [HunterAnnotation]
    let registrations: [Registration]
    let currentChickenId: String
    let canDesignate: Bool
    let onDesignateTapped: (Registration) -> Void

    /// PP-86 — only registered hunters (in `registrations`) who are NOT
    /// the current chicken can be re-designated. We surface the
    /// `teamName` rather than the technical UID.
    private var designatableRegistrations: [Registration] {
        registrations.filter { $0.userId != currentChickenId }
    }

    var body: some View {
        List {
            Section {
                ForEach(hunterAnnotations) { hunter in
                    HStack {
                        Image(systemName: "figure.run")
                            .foregroundStyle(Color.CROrange)
                        VStack(alignment: .leading) {
                            Text(displayName(for: hunter.id))
                                .font(.headline)
                            Text("Live position")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            } header: {
                Text("Connected (\(hunterAnnotations.count) / \(game.hunterIds.count))")
            }
            if game.hunterIds.count > hunterAnnotations.count {
                Section {
                    ForEach(game.hunterIds.filter { hid in !hunterAnnotations.contains(where: { $0.id == hid }) }, id: \.self) { hid in
                        HStack {
                            Image(systemName: "person.fill.questionmark")
                                .foregroundStyle(.secondary)
                            Text(displayName(for: hid))
                                .foregroundStyle(.secondary)
                        }
                    }
                } header: {
                    Text("Registered but not transmitting")
                }
            }
            if canDesignate && !designatableRegistrations.isEmpty {
                Section {
                    ForEach(designatableRegistrations) { reg in
                        Button {
                            onDesignateTapped(reg)
                        } label: {
                            HStack {
                                Image(systemName: "crown.fill")
                                    .foregroundStyle(Color.CRPink)
                                VStack(alignment: .leading) {
                                    Text(reg.teamName)
                                        .font(.headline)
                                        .foregroundStyle(Color.onBackground)
                                    Text("Designate as chicken")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                } header: {
                    Text("Designate the chicken")
                } footer: {
                    Text("The designated hunter becomes the chicken; they leave the hunters list. Only possible before the game starts.")
                }
            }
        }
        .navigationTitle("Hunters")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func displayName(for uid: String) -> String {
        registrations.first(where: { $0.userId == uid })?.teamName ?? "Hunter"
    }
}

private struct GameInfoView: View {
    let game: Game

    var body: some View {
        List {
            LabeledContent("Game code", value: game.gameCode)
            LabeledContent("Mode", value: game.gameMode.title)
            LabeledContent("Hunters", value: "\(game.hunterIds.count)")
            LabeledContent("Winners", value: "\(game.winners.count)")
        }
        .navigationTitle(game.name.isEmpty ? "Game" : game.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}
