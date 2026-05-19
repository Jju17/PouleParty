//
//  AppDelegate.swift
//  PouleParty
//

import Firebase
import FirebaseAppCheck
import FirebaseMessaging
import os
import UIKit
import UserNotifications

extension Notification.Name {
    static let pouleDeeplink = Notification.Name("dev.rahier.pouleparty.deeplink")
}

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // CRIT-4 (audit 2026-05-17): App Check provider factory MUST be set
        // before `FirebaseApp.configure()` — Firebase reads it once at
        // configure-time and caches the choice for the process lifetime.
        AppCheck.setAppCheckProviderFactory(PoulePartyAppCheckProviderFactory())
        FirebaseApp.configure()
        MigrationManager.runIfNeeded()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        // Apple guideline 4.5.4: do not register for remote notifications before the user
        // has granted permission. On subsequent launches, register only if already authorized.
        // The onboarding notification slide triggers registration on first grant via NotificationClient.
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            if settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }
        return true
    }

    /// PP-52 fallback path for Universal Links. iOS SwiftUI App
    /// lifecycle has `.onOpenURL` as the primary hook (which works
    /// on iPhone — see `PoulePartyApp`); this UIKit method covers
    /// macOS Catalyst + iPad multi-window scenarios where the
    /// SwiftUI modifier may not fire. Forwards to the same
    /// `.pouleDeeplink` notification the App scene observes.
    func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL
        else {
            return false
        }
        Logger(category: "PP-52-Deeplink").notice("[AppDelegate.continue] forwarding url=\(url.absoluteString, privacy: .public)")
        NotificationCenter.default.post(
            name: .pouleDeeplink,
            object: nil,
            userInfo: ["url": url]
        )
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // MARK: - MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        Task {
            await FCMTokenManager.shared.saveToken(token)
        }
    }

    // MARK: - UNUserNotificationCenterDelegate

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}
