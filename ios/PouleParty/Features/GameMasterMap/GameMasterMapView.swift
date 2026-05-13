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
                        hunterAnnotations: store.hunterAnnotations
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
    }
}

private struct GameMasterHuntersListView: View {
    let game: Game
    let hunterAnnotations: [HunterAnnotation]

    var body: some View {
        List {
            Section {
                ForEach(hunterAnnotations) { hunter in
                    HStack {
                        Image(systemName: "figure.run")
                            .foregroundStyle(Color.CROrange)
                        VStack(alignment: .leading) {
                            Text(hunter.displayName)
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
                            Text("Hunter (no position yet)")
                                .foregroundStyle(.secondary)
                        }
                    }
                } header: {
                    Text("Registered but not transmitting")
                }
            }
        }
        .navigationTitle("Hunters")
        .navigationBarTitleDisplayMode(.inline)
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
