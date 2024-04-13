//
//  Selection.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct SelectionFeature {

    @ObservableState
    struct State { 
        @Presents var destination: Destination.State?
    }

    enum Action {
        case chickenButtonTapped
        case destination(PresentationAction<Destination.Action>)
        case dismissChickenConfig
        case goToChickenConfigTriggered
        case goToChickenMapTriggered(Game)
        case hunterButtonTapped
    }

    @Reducer
    struct Destination {
        enum State {
            case alert(AlertState<Action.Alert>)
            case chickenConfig(ChickenConfigFeature.State)
        }

        enum Action {
            case alert(Alert)
            case chickenConfig(ChickenConfigFeature.Action)

            enum Alert {
                case addAlertHere
            }
        }

        var body: some ReducerOf<Self> {
            Scope(state: \.chickenConfig, action: \.chickenConfig) {
                ChickenConfigFeature()
            }
        }
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        Reduce {
            state,
            action in
            switch action {
            case .chickenButtonTapped:
                return .run { send in
                    let game = await apiClient.getConfig()
                    
                    guard game != nil
                    else { 
                        await send(.goToChickenConfigTriggered)
                        return
                    }
                    
                    if game!.endDate > .now {
                        await send(.goToChickenMapTriggered(game!))
                    } else if game!.endDate < .now {
                        try await apiClient.deleteConfig()
                        await send(.goToChickenConfigTriggered)
                    }
                }
            case let .destination(.presented(.chickenConfig(.startGameTriggered(game)))):
                state.destination = nil
                return .run { send in
                    await send(.goToChickenMapTriggered(game))
                }
            case .destination:
                return .none
            case .dismissChickenConfig:
                state.destination = nil
                return .none
            case .goToChickenConfigTriggered:
                state.destination = .chickenConfig(
                    ChickenConfigFeature.State(game:Shared(Game(id: UUID().uuidString)))
                )
                return .none
            case .goToChickenMapTriggered:
                return .none
            case .hunterButtonTapped:
                return .none
            }
        }
        .ifLet(\.$destination, action: \.destination) {
          Destination()
        }
    }
}



struct SelectionView: View {
    @Bindable var store: StoreOf<SelectionFeature>
    private let selectionChickenTip = SelectChickenTip()
    private let selectionHunterTip = SelectHunterTip()

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            VStack(alignment: .center, spacing: 30) {
                HStack(alignment: .center, spacing: 0) {
                    Spacer()
                    Image(systemName: "info.circle")
                        .resizable()
                        .scaledToFit()
                        .foregroundStyle(.white)
                        .frame(width: 25, height: 25, alignment: .center)
                }
                Spacer()
                VStack(alignment: .center, spacing: 0) {
                    SelectionButton(" Chicken ") {
                        self.store.send(.chickenButtonTapped)
                    }
                    .frame(height: 50)

                    Text(" VS ")
                        .font(.banger(size: 34))
                        .foregroundStyle(.white)
                        .padding(.vertical, 30)

                    SelectionButton(" Hunter ") {
                        self.store.send(.hunterButtonTapped)
                    }
                    .frame(height: 50)
                }
                Spacer()
            }
            .padding()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(LinearGradient(colors: [.crOrange, .crPink], startPoint: .top, endPoint: .bottom))
        .sheet(
            item: $store.scope(
                state: \.destination?.chickenConfig,
                action: \.destination.chickenConfig
            )
        ) { store in
            NavigationStack {
                ChickenConfigView(store: store)
                    .toolbar {
                        ToolbarItem {
                            Button {
                                self.store.send(.dismissChickenConfig)
                            } label: {
                                Image(systemName: "xmark")
                            }

                        }
                    }
            }
        }

    }
}

#Preview {
    SelectionView(
        store:
            Store(initialState: SelectionFeature.State()) {
                SelectionFeature()
            }
    )
}
