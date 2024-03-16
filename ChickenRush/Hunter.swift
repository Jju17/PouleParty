//
//  Hunter.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct HunterFeature {

    @ObservableState
    struct State {

    }

    enum Action {

    }

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {

            }
        }
    }

}


struct HunterView: View {
    var store: StoreOf<HunterFeature>

    var body: some View {
        Text("Hello, World!")
    }
}

#Preview {
    HunterView(store: Store(initialState: HunterFeature.State(), reducer: {
        HunterFeature()
    }))
}
