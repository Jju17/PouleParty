//
//  AppDelegate.swift
//  PouleParty
//

import Firebase
import FirebaseAppCheck
import FirebaseMessaging
import UIKit
import UserNotifications

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
