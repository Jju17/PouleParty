//
//  AuthClient.swift
//  PouleParty
//

import ComposableArchitecture
import FirebaseAuth
import FirebaseMessaging

struct UserClient {
    var currentUserId: () -> String?
    var deleteAccount: () async throws -> Void
    var fcmToken: () -> String?
    var signInAnonymously: () async throws -> String
}

extension UserClient: TestDependencyKey {
    static let testValue = UserClient(
        currentUserId: { "test-auth-uid" },
        deleteAccount: { },
        fcmToken: { "test-fcm-token" },
        signInAnonymously: { "test-auth-uid" }
    )
}

extension UserClient: DependencyKey {
    static var liveValue = UserClient(
        currentUserId: {
            Auth.auth().currentUser?.uid
        },
        deleteAccount: {
            try await Auth.auth().currentUser?.delete()
            _ = try await Auth.auth().signInAnonymously()
        },
        fcmToken: {
            Messaging.messaging().fcmToken
        },
        signInAnonymously: {
            if let uid = Auth.auth().currentUser?.uid {
                return uid
            }
            let result = try await Auth.auth().signInAnonymously()
            return result.user.uid
        }
    )
}

extension DependencyValues {
    var userClient: UserClient {
        get { self[UserClient.self] }
        set { self[UserClient.self] = newValue }
    }
}
