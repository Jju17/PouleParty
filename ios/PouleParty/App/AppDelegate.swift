//
//  AppDelegate.swift
//  PouleParty
//

import ComposableArchitecture
import Firebase
import FirebaseAppCheck
import FirebaseMessaging
import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    @Dependency(\.remoteConfigClient) var remoteConfigClient

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // CRIT-4 (audit 2026-05-17): App Check provider factory MUST be set
        // before `FirebaseApp.configure()` — Firebase reads it once at
        // configure-time and caches the choice for the process lifetime.
        AppCheck.setAppCheckProviderFactory(PoulePartyAppCheckProviderFactory())
        FirebaseApp.configure()
        // Remote Config: pull the latest tunable game values (admin code,
        // found-code cooldown, default zone radius). Getters return the
        // compiled defaults until this first fetch activates.
        Task { await remoteConfigClient.activate() }
        // Bigger `URLCache.shared` so AsyncImage / AVPlayer responses
        // backed by Firebase Storage hit local disk on revisit instead
        // of re-downloading. iOS defaults to 4 MB memory / 20 MB disk,
        // which is too small for the validator queue's 10-20 image
        // submissions per game. 50 MB memory / 250 MB disk gives the
        // validator instant scroll-back after the first load. Cache
        // bytes are evicted by iOS under pressure, so this is safe.
        URLCache.shared = URLCache(
            memoryCapacity: 50 * 1024 * 1024,
            diskCapacity: 250 * 1024 * 1024
        )
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
