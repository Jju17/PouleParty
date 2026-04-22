//
//  AppDelegate.swift
//  PouleParty
//

import Firebase
import FirebaseMessaging
import os
import StripePaymentSheet
import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        MigrationManager.runIfNeeded()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        configureStripe()
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

    private func configureStripe() {
        let key = Bundle.main.object(forInfoDictionaryKey: "StripePublishableKey") as? String ?? ""
        guard key.hasPrefix("pk_") else {
            // assertionFailure is a no-op in Release; we still want a loud
            // signal in production logs so the missing key isn't silent.
            Logger(category: "AppDelegate").error("StripePublishableKey missing/invalid in Info.plist (got prefix \"\(key.prefix(4))\") — set STRIPE_PUBLISHABLE_KEY in project build settings. Stripe-paid flows will fail at PaymentSheet open.")
            #if DEBUG
            assertionFailure("StripePublishableKey missing/invalid in Info.plist")
            #endif
            return
        }
        StripeAPI.defaultPublishableKey = key
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        if StripeAPI.handleURLCallback(with: url) { return true }
        return false
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
