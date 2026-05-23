//
//  PoulePartyApp.swift
//  PouleParty
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import Sharing
import SwiftUI
import Foundation

@main
struct PoulePartyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @Shared(.appStorage(AppConstants.prefOnboardingCompleted)) var hasCompletedOnboarding = false

    private let store: StoreOf<AppFeature>

    init() {
        let completed = UserDefaults.standard.bool(forKey: AppConstants.prefOnboardingCompleted)
        store = Store(
            initialState: completed
                ? AppFeature.State.home(HomeFeature.State())
                : AppFeature.State.onboarding(OnboardingFeature.State())
        ) {
            AppFeature()
        }
    }

    var body: some Scene {
        WindowGroup {
            AppView(store: store)
        }
    }
}
