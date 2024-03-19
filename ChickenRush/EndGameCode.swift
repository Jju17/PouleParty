//
//  EndGameCode.swift
//  ChickenRush
//
//  Created by Julien Rahier on 19/03/2024.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct EndGameCodeFeature {
    struct State { }
    enum Action { }

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action { }
        }
    }
}

struct EndGameCodeView: View {
    var store: StoreOf<EndGameCodeFeature>

    var body: some View {
        Text("Hello, World!")
    }
}

#Preview {
    EndGameCodeView(store: Store(initialState: EndGameCodeFeature.State()) {
        EndGameCodeFeature()
    })
}
