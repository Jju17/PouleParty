//
//  RandomDrink.swift
//  ChickenRush
//
//  Created by Julien Rahier on 17/03/2024.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct DrinksLotteryFeature {

    @ObservableState
    struct State { }

    enum Action { }

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {

            }
        }
    }


}

struct DrinksLotteryView: View {
    var store: StoreOf<DrinksLotteryFeature>

    var body: some View {
        VStack {
            Text("Hello, World!")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.linearGradient(Gradient(colors: [.CROrange, .CRPink]), startPoint: .top, endPoint: .bottom))

    }
}

#Preview {
    DrinksLotteryView(store: Store(initialState: DrinksLotteryFeature.State()) {
        DrinksLotteryFeature()
    })
}
