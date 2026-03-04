//
//  AuthClient.swift
//  PouleParty
//

import ComposableArchitecture
import FirebaseAuth

struct UserClient {
    var currentUserId: () -> String?
    var signInAnonymously: () async throws -> String
    var deleteAccount: () async throws -> Void
}

extension UserClient: TestDependencyKey {
    static let testValue = UserClient(
        currentUserId: { "test-auth-uid" },
        signInAnonymously: { "test-auth-uid" },
        deleteAccount: { }
    )
}

extension UserClient: DependencyKey {
    static var liveValue = UserClient(
        currentUserId: {
            Auth.auth().currentUser?.uid
        },
        signInAnonymously: {
            if let uid = Auth.auth().currentUser?.uid {
                return uid
            }
            let result = try await Auth.auth().signInAnonymously()
            return result.user.uid
        },
        deleteAccount: {
            try await Auth.auth().currentUser?.delete()
            _ = try await Auth.auth().signInAnonymously()
        }
    )
}

extension DependencyValues {
    var userClient: UserClient {
        get { self[UserClient.self] }
        set { self[UserClient.self] = newValue }
    }
}
