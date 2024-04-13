//
//  Onboarding.swift
//  ChickenRush
//
//  Created by Julien Rahier on 05/04/2024.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct OnboardingFeature {
    struct State { }
    enum Action { }

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action { }
        }
    }
}

struct OnboardingView: View {
    var store: StoreOf<OnboardingFeature>

    var body: some View {
        Text("Hello, World!")
    }
}

#Preview {
    OnboardingView(store: Store(initialState: OnboardingFeature.State()) {
        OnboardingFeature()
    })
}
