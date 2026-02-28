//
//  AuthClient.swift
//  PouleParty
//

import ComposableArchitecture
import FirebaseAuth

struct AuthClient {
    var currentUserId: () -> String?
    var signInAnonymously: () async throws -> String
}

extension AuthClient: TestDependencyKey {
    static let testValue = AuthClient(
        currentUserId: { "test-auth-uid" },
        signInAnonymously: { "test-auth-uid" }
    )
}

extension AuthClient: DependencyKey {
    static var liveValue = AuthClient(
        currentUserId: {
            Auth.auth().currentUser?.uid
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
    var authClient: AuthClient {
        get { self[AuthClient.self] }
        set { self[AuthClient.self] = newValue }
    }
}
