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
import os

@main
struct PoulePartyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @Shared(.appStorage(AppConstants.prefOnboardingCompleted)) var hasCompletedOnboarding = false

    // Single root store held by the App scene so the
    // `.onContinueUserActivity` modifier can dispatch deeplink
    // actions directly into it (vs. the inner AppView which doesn't
    // bubble the store back up).
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
                // PP-52 — primary Universal Link path on iOS SwiftUI
                // App lifecycle. `.onContinueUserActivity` does NOT
                // fire for Universal Links when the app uses the
                // SwiftUI `App` lifecycle (known SwiftUI limitation
                // — the NSUserActivity is silently dropped). The
                // `.onOpenURL` hook fires for both custom URL
                // schemes AND Universal Links once the app is open.
                .onOpenURL { url in
                    handleDeeplink(url)
                }
                // Belt-and-suspenders for macOS Catalyst / iPad
                // multi-window scenarios where the SwiftUI primary
                // hook may not fire. AppDelegate.continue posts to
                // `.pouleDeeplink` which feeds the same handler.
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    handleDeeplink(activity.webpageURL)
                }
                .onReceive(NotificationCenter.default.publisher(for: .pouleDeeplink)) { notification in
                    handleDeeplink(notification.userInfo?["url"] as? URL)
                }
        }
    }

    /// PP-52 — Universal Link entry point. Parses
    /// `https://pouleparty.be/join?code=ABCDEF` and forwards the
    /// validation code to `AppFeature`. Anything other than `/join`
    /// is silently ignored so we don't fight other deeplink owners
    /// added later.
    private func handleDeeplink(_ url: URL?) {
        guard let url,
              url.host == "pouleparty.be",
              url.path == "/join" || url.path.hasPrefix("/join/")
        else { return }
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        guard let code = components?.queryItems?.first(where: { $0.name == "code" })?.value,
              !code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return }
        Logger(category: "PP-52-Deeplink").notice("[PoulePartyApp] parsed code=\(code, privacy: .public)")
        store.send(.deeplinkValidationCodeReceived(code))
    }
}
