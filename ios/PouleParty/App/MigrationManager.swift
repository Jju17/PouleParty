import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import Foundation
import os

enum MigrationManager {
    static func runIfNeeded() {
        let defaults = UserDefaults.standard
        let lastVersion = defaults.string(forKey: AppConstants.prefLastMigratedVersion) ?? "0.0.0"
        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"

        if lastVersion.compare("1.4.0", options: .numeric) == .orderedAscending {
            migrateUserProfile()
        }

        defaults.set(currentVersion, forKey: AppConstants.prefLastMigratedVersion)
    }

    private static func migrateUserProfile() {
        guard FirebaseApp.app() != nil,
              let userId = Auth.auth().currentUser?.uid else { return }
        let nickname = UserDefaults.standard.string(forKey: AppConstants.prefUserNickname) ?? ""
        var data: [String: Any] = ["updatedAt": FieldValue.serverTimestamp()]
        if !nickname.isEmpty {
            data["nickname"] = nickname
        }
        Firestore.firestore()
            .collection("users").document(userId)
            .setData(data, merge: true) { error in
                if let error {
                    Logger(category: "Migration").warning("[profile] migration write failed: \(error.localizedDescription)")
                }
            }
    }
}
