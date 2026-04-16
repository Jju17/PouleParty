//
//  FCMTokenManager.swift
//  PouleParty
//

import FirebaseAuth
import FirebaseFirestore
import os

actor FCMTokenManager {
    static let shared = FCMTokenManager()

    private let logger = Logger(subsystem: "dev.rahier.pouleparty", category: "FCMTokenManager")

    func saveToken(_ token: String) {
        guard let userId = Auth.auth().currentUser?.uid else {
            logger.warning("Cannot save FCM token — no authenticated user")
            return
        }

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
