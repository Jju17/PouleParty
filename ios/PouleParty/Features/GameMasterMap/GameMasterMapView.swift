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
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
            .overlay(alignment: .top) {
                if store.isGameOver {
                    GameEndedBanner(onTap: {
                        store.send(.view(.viewLeaderboardTapped))
                    })
                    .padding(.top, 76)
                    .padding(.horizontal, 16)
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.25), value: store.isGameOver)
            // PP-25: validation queue presents as a fullScreenCover to
            // avoid sheet stacking conflicts with the hunters / info
            // sheets attached to the same view (SwiftUI gets confused
            // with >2 sheet modifiers and may freeze when a new sheet
            // tries to present while others are pending).
            .fullScreenCover(item: $store.scope(state: \.validationQueue, action: \.validationQueue)) { vqStore in
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
                GameInfoSheet(
                    game: store.game,
                    onCancelGame: { store.send(.view(.leaveGameTapped)) },
                    leaveGameLabel: "Leave game"
                )
            }
            .overlay {
                if store.game.status == .readyToLaunch {
                    PreGameOverlay(
                        role: .gameMaster,
                        gameModTitle: store.game.gameMode.title,
                        gameCode: store.game.gameCode,
                        targetDate: store.game.startDate,
                        nowDate: store.nowDate,
                        connectedHunters: store.game.hunterIds.count,
                        isManualStart: true,
                        isLaunching: store.isLaunching,
                        launchErrorMessage: store.launchError,
                        onLaunchTapped: { store.send(.view(.launchTapped)) },
                        onLaunchErrorDismissed: { store.send(.view(.launchErrorDismissed)) }
                    )
                } else if !store.hasGameStarted {
                    PreGameOverlay(
                        role: .gameMaster,
                        gameModTitle: store.game.gameMode.title,
                        gameCode: store.game.gameCode,
                        targetDate: store.game.startDate,
                        nowDate: store.nowDate,
                        connectedHunters: store.game.hunterIds.count
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

