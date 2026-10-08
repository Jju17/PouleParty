import ComposableArchitecture
import Foundation

private enum NowKey: DependencyKey {
    static let liveValue = DateGenerator { Date() }
    static let testValue = DateGenerator { Date() }
}

extension DependencyValues {
    /// The wall clock reducers read; tests pin it with `$0.now = .constant(...)`.
    var now: DateGenerator {
        get { self[NowKey.self] }
        set { self[NowKey.self] = newValue }
    }
}
