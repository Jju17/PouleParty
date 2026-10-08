import FirebaseAuth
import FirebaseFirestore
import os

actor FCMTokenManager {
    static let shared = FCMTokenManager()

    private let logger = Logger(category: "FCMTokenManager")
    /// The token can arrive before the anonymous sign-in finishes on first launch.
    private var pendingToken: String?

    func userSignedIn() {
        guard let pendingToken else { return }
        saveToken(pendingToken)
    }

    func saveToken(_ token: String) {
        guard let userId = Auth.auth().currentUser?.uid else {
            pendingToken = token
            return
        }
        pendingToken = nil

        var data: [String: Any] = [
            "token": token,
            "platform": "ios",
            "updatedAt": FieldValue.serverTimestamp()
        ]

        // Always include the nickname so it's restored if the document was recreated
        let nickname = UserDefaults.standard.string(forKey: AppConstants.prefUserNickname)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !nickname.isEmpty {
            data["nickname"] = nickname
        }

        let ref = Firestore.firestore().collection("users").document(userId)
        ref.setData(data, merge: true) { [logger] error in
            if let error {
                logger.error("Failed to save FCM token: \(error.localizedDescription)")
            }
        }
    }

}
