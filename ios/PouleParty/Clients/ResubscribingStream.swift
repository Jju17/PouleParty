import Foundation
import os

private let logger = Logger(category: "Streams")

func resubscribeDelay(failures: Int, cap: Duration) -> Duration {
    min(cap, .seconds(1 << min(failures, 10)))
}

/// Firebase listeners stop for good after an error. This keeps the last value
/// on screen and attaches again with a capped backoff; `whenDenied` hides a
/// marker whose read was revoked (radar ping over, invisibility).
func resubscribingStream<Value>(
    _ operation: String,
    cap: Duration = .seconds(30),
    whenDenied: Value? = nil,
    attach: @escaping (_ yield: @escaping (Value) -> Void, _ fail: @escaping (Error) -> Void) -> () -> Void
) -> AsyncStream<Value> {
    AsyncStream { continuation in
        let subscription = Subscription(operation: operation, cap: cap, whenDenied: whenDenied, continuation: continuation, attach: attach)
        continuation.onTermination = { _ in subscription.stop() }
        subscription.start()
    }
}

private final class Subscription<Value>: @unchecked Sendable {
    private let operation: String
    private let cap: Duration
    private let whenDenied: Value?
    private let continuation: AsyncStream<Value>.Continuation
    private let attach: (_ yield: @escaping (Value) -> Void, _ fail: @escaping (Error) -> Void) -> () -> Void
    private let lock = NSRecursiveLock()
    private var detach: (() -> Void)?
    private var retry: Task<Void, Never>?
    private var failures = 0
    private var stopped = false

    init(
        operation: String,
        cap: Duration,
        whenDenied: Value?,
        continuation: AsyncStream<Value>.Continuation,
        attach: @escaping (_ yield: @escaping (Value) -> Void, _ fail: @escaping (Error) -> Void) -> () -> Void
    ) {
        self.operation = operation
        self.cap = cap
        self.whenDenied = whenDenied
        self.continuation = continuation
        self.attach = attach
    }

    func start() {
        lock.lock()
        defer { lock.unlock() }
        guard !stopped else { return }
        detach = attach(
            { [weak self] value in
                guard let self else { return }
                self.lock.withLock { self.failures = 0 }
                self.continuation.yield(value)
            },
            { [weak self] error in self?.fail(error) }
        )
    }

    private func fail(_ error: Error) {
        logger.warning("\(self.operation) listener stopped: \(error.localizedDescription)")
        if let whenDenied { continuation.yield(whenDenied) }
        lock.lock()
        defer { lock.unlock() }
        detach?()
        detach = nil
        guard !stopped else { return }
        let delay = resubscribeDelay(failures: failures, cap: cap)
        failures += 1
        retry = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            self?.start()
        }
    }

    func stop() {
        lock.lock()
        defer { lock.unlock() }
        stopped = true
        retry?.cancel()
        detach?()
        detach = nil
    }
}
