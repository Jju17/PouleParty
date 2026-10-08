import ComposableArchitecture
import FirebaseAuth
import FirebaseFirestore
import os

private let logger = Logger(category: "UserClient")

struct SignInResult: Equatable {
    let uid: String
    let isNewUser: Bool
}

struct UserClient {
    var currentUserId: () -> String?
    var deleteAccount: () async throws -> Void
    var syncPushToken: () async -> Void
    var saveNickname: (String) async -> Void
    var signInAnonymously: () async throws -> SignInResult
}

extension UserClient: TestDependencyKey {
    static let testValue = UserClient(
        currentUserId: { "test-auth-uid" },
        deleteAccount: { },
        syncPushToken: { },
        saveNickname: { _ in },
        signInAnonymously: { SignInResult(uid: "test-auth-uid", isNewUser: false) }
    )
}

extension UserClient: DependencyKey {
    static let liveValue = UserClient(
        currentUserId: {
            Auth.auth().currentUser?.uid
        },
        deleteAccount: {
            // Delete the user's Firestore profile doc while still authenticated
            // (rules require auth.uid == userId). Auth deletion can only happen
            // AFTER the Firestore delete because the rules check disappears once
            // the auth user is gone.
            if let userId = Auth.auth().currentUser?.uid {
                try await Firestore.firestore()
                    .collection("users").document(userId)
                    .delete()
            }
            try await Auth.auth().currentUser?.delete()
            _ = try await Auth.auth().signInAnonymously()
        },
        syncPushToken: {
            await FCMTokenManager.shared.userSignedIn()
        },
        saveNickname: { nickname in
            guard let userId = Auth.auth().currentUser?.uid else { return }
            do {
                try await Firestore.firestore()
                    .collection("users").document(userId)
                    .setData(["nickname": nickname, "updatedAt": FieldValue.serverTimestamp()], merge: true)
            } catch {
                logger.warning("[profile] nickname sync failed: \(error.localizedDescription)")
            }
        },
        signInAnonymously: {
            if let uid = Auth.auth().currentUser?.uid {
                return SignInResult(uid: uid, isNewUser: false)
            }
            let result = try await Auth.auth().signInAnonymously()
            let isNew = result.additionalUserInfo?.isNewUser ?? true
            return SignInResult(uid: result.user.uid, isNewUser: isNew)
        }
    )
}

extension DependencyValues {
    var userClient: UserClient {
        get { self[UserClient.self] }
        set { self[UserClient.self] = newValue }
    }
}
