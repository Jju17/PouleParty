import SwiftUI

extension View {
    /// Reads an error aloud with VoiceOver when it appears or changes.
    func announcedError(_ message: String) -> some View {
        onAppear { AccessibilityNotification.Announcement(message).post() }
            .onChange(of: message) { _, newValue in AccessibilityNotification.Announcement(newValue).post() }
    }
}
