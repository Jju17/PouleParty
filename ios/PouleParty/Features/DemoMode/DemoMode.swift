//
//  DemoMode.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct DemoModeFeature {

    enum Tab: String, CaseIterable, Equatable {
        case chickenMap
        case hunterMap
        case gameMasterMap
        case victory
    }

    @ObservableState
    struct State: Equatable {
        var selectedTab: Tab = .chickenMap
    }

    enum Action {
        case tabSelected(Tab)
        case exitTapped
        case delegate(Delegate)

        @CasePathable
        enum Delegate: Equatable {
            case exitDemo
        }
    }

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case let .tabSelected(tab):
                state.selectedTab = tab
                return .none
            case .exitTapped:
                return .send(.delegate(.exitDemo))
            case .delegate:
                return .none
            }
        }
    }
}

struct DemoModeView: View {
    @Bindable var store: StoreOf<DemoModeFeature>

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                BangerText(String(localized: "Demo Mode"), size: 24)
                    .foregroundStyle(Color.onBackground)
                Spacer()
                Button {
                    store.send(.exitTapped)
                } label: {
                    Text("Quit Demo")
                        .font(.gameboy(size: 10))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .foregroundStyle(Color.onBackground)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.onBackground, lineWidth: 2)
                        )
                }
                .accessibilityLabel("Quit Demo")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.gradientBackgroundWarmth)

            Picker("Demo View", selection: $store.selectedTab.sending(\.tabSelected)) {
                Text("🐔 Chicken").tag(DemoModeFeature.Tab.chickenMap)
                Text("🏃 Hunter").tag(DemoModeFeature.Tab.hunterMap)
                Text("👁 GM").tag(DemoModeFeature.Tab.gameMasterMap)
                Text("🏆 Victory").tag(DemoModeFeature.Tab.victory)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.gradientBackgroundWarmth)

            Group {
                switch store.selectedTab {
                case .chickenMap: chickenMapTab
                case .hunterMap: hunterMapTab
                case .gameMasterMap: gameMasterMapTab
                case .victory: victoryTab
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.gradientBackgroundWarmth.ignoresSafeArea())
    }

    private var chickenMapTab: some View {
        ChickenMapView(
            store: Store(
                initialState: ChickenMapFeature.State(game: MockDemoData.liveGame)
            ) {
                ChickenMapFeature()
            } withDependencies: {
                $0.apiClient = .demo
                $0.locationClient = .demo
                $0.userClient = .demo
            }
        )
    }

    private var hunterMapTab: some View {
        HunterMapView(
            store: Store(
                initialState: HunterMapFeature.State(
                    game: MockDemoData.liveGame,
                    hunterId: MockDemoData.hunterIds[0],
                    hunterName: "Red Foxes"
                )
            ) {
                HunterMapFeature()
            } withDependencies: {
                $0.apiClient = .demo
                $0.locationClient = .demo
                $0.userClient = .demo
                $0.userClient.currentUserId = { MockDemoData.hunterIds[0] }
            }
        )
    }

    private var gameMasterMapTab: some View {
        GameMasterMapView(
            store: Store(
                initialState: GameMasterMapFeature.State(game: MockDemoData.liveGame)
            ) {
                GameMasterMapFeature()
            } withDependencies: {
                $0.apiClient = .demo
                $0.locationClient = .demo
                $0.userClient = .demo
                $0.userClient.currentUserId = { MockDemoData.gameMasterUid }
            }
        )
    }

    private var victoryTab: some View {
        VictoryView(
            store: Store(
                initialState: VictoryFeature.State(
                    game: MockDemoData.doneGame,
                    hunterId: MockDemoData.hunterIds[0],
                    hunterName: "Red Foxes"
                )
            ) {
                VictoryFeature()
            } withDependencies: {
                $0.apiClient = .demo
                $0.locationClient = .demo
                $0.userClient = .demo
                $0.userClient.currentUserId = { MockDemoData.hunterIds[0] }
            }
        )
    }
}

#Preview {
    DemoModeView(
        store: Store(initialState: DemoModeFeature.State()) {
            DemoModeFeature()
        }
    )
}
