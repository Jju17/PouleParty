//
//  ChickenRushApp.swift
//  ChickenRush
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import Firebase
import SwiftUI
import Foundation

@main
struct ChickenRushApp: App {

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            AppView(
                store: Store(
                    initialState: UserDefaults.standard.bool(forKey: "hasCompletedOnboarding")
                        ? AppFeature.State.selection(SelectionFeature.State())
                        : AppFeature.State.onboarding(OnboardingFeature.State())
                ) {
                    AppFeature()
                }
            )
        }
    }
}
