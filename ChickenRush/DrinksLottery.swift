//
//  DrinksLottery.swift
//  ChickenRush
//
//  Created by Julien Rahier on 17/03/2024.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct DrinksLotteryFeature {

    @ObservableState
    struct State { 
        var showAnimation: Bool = false
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case checkmarkButtonTapped
        case xmarkButtonTapped
    }

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .checkmarkButtonTapped:
                withAnimation {
                    state.showAnimation = true
                }
                return .none
            case .xmarkButtonTapped:
                    state.showAnimation = false
                return .none
            }
        }
    }


}

struct DrinksLotteryView: View {
    @Bindable var store: StoreOf<DrinksLotteryFeature>

    var body: some View {
        VStack {
            Text("Was the Chicken inside the bar ?")
                .font(.title)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .fontWeight(.bold)
            Spacer()
            DrinksListView(isAnimActive: $store.showAnimation, drinks: Drink.mockList)
            Spacer()
            HStack(spacing: 0) {
                Spacer()
                Button(
                    action: {
                        store.send(.xmarkButtonTapped)
                    },
                    label: {
                        Image(systemName: "xmark.circle.fill")
                            .resizable()
                            .frame(width: 70, height: 70)
                            .foregroundStyle(.white)
                    }
                )
                Spacer()

                Button(
                    action: {
                        store.send(.checkmarkButtonTapped)
                    },
                    label: {
                        Image(systemName: "checkmark.circle.fill")
                            .resizable()
                            .frame(width: 70, height: 70)
                            .foregroundStyle(.white)
                    }
                )
                Spacer()
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.linearGradient(Gradient(colors: [.CROrange, .CRPink]), startPoint: .top, endPoint: .bottom))
    }
}

#Preview {
    DrinksLotteryView(store: Store(initialState: DrinksLotteryFeature.State()) {
        DrinksLotteryFeature()
    })
}
