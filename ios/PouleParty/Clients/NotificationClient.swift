//
//  NotificationClient.swift
//  PouleParty
//

import ComposableArchitecture
import UserNotifications

struct NotificationClient {
    var authorizationStatus: () async -> UNAuthorizationStatus
    var requestAuthorization: () async -> UNAuthorizationStatus
}

extension NotificationClient: TestDependencyKey {
    static let testValue = NotificationClient(
        authorizationStatus: { .notDetermined },
        requestAuthorization: { .authorized }
    )
}

extension NotificationClient: DependencyKey {
    static var liveValue = NotificationClient(
        authorizationStatus: {
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            return settings.authorizationStatus
        },
        requestAuthorization: {
            let center = UNUserNotificationCenter.current()
            do {
                _ = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            } catch {
                // Silently handle — user denied or error
            }
            let settings = await center.notificationSettings()
            return settings.authorizationStatus
        }
    )
}

extension DependencyValues {
    var notificationClient: NotificationClient {
        get { self[NotificationClient.self] }
        set { self[NotificationClient.self] = newValue }
    }
}
