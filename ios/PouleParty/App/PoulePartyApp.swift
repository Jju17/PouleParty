//
//  PoulePartyApp.swift
//  PouleParty
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import Firebase
import Sharing
import SwiftUI
import Foundation

@main
struct PoulePartyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @Shared(.appStorage(AppConstants.prefOnboardingCompleted)) var hasCompletedOnboarding = false

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            AppView(
                store: Store(
                    initialState: hasCompletedOnboarding
                        ? AppFeature.State.home(HomeFeature.State())
                        : AppFeature.State.onboarding(OnboardingFeature.State())
                ) {
                    AppFeature()
                }
            )
        }
    }
}
