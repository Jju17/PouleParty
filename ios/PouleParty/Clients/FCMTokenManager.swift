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

        let ref = Firestore.firestore().collection("users").document(userId)
        ref.setData([
            "token": token,
            "platform": "ios",
            "updatedAt": FieldValue.serverTimestamp()
        ], merge: true) { [logger] error in
            if let error {
                logger.error("Failed to save FCM token: \(error.localizedDescription)")
            }
        }
    }

}
