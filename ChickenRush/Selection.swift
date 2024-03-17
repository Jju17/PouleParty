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
    struct State { }

    enum Action {
        case chickenButtonTapped
        case hunterButtonTapped
    }

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .chickenButtonTapped:
                return .none
            case .hunterButtonTapped:
                return .none
            }
        }
    }
}



struct SelectionView: View {
    @Bindable var store: StoreOf<SelectionFeature>

    var body: some View {
        VStack(alignment: .center, spacing: 30) {
            Button(
                action: {
                    self.store.send(.chickenButtonTapped)
                },
                label: {
                    Text("Chicken")
                }
            )
            Text("VS")
            Button(
                action: {
                    self.store.send(.hunterButtonTapped)
                },
                label: {
                    Text("Hunter")
                }
            )
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
