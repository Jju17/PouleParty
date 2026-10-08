import ComposableArchitecture
import UIKit

/// Haptic feedback as a dependency so reducers stay testable.
struct HapticClient {
    var notify: (UINotificationFeedbackGenerator.FeedbackType) -> Void
}

extension HapticClient: DependencyKey {
    static let liveValue = HapticClient(notify: { HapticManager.notification($0) })
    static let testValue = HapticClient(notify: { _ in })
}

extension DependencyValues {
    var hapticClient: HapticClient {
        get { self[HapticClient.self] }
        set { self[HapticClient.self] = newValue }
    }
}
