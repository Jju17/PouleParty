//
//  LocationClient.swift
//  PouleParty
//
//  Created by Julien Rahier on 13/02/2026.
//

import ComposableArchitecture
import CoreLocation
import Foundation

struct LocationClient {
    var authorizationStatus: () -> CLAuthorizationStatus
    var lastLocation: () -> CLLocationCoordinate2D?
    var requestWhenInUse: () async -> Void
    var requestAlways: () async -> Void
    var startTracking: () -> AsyncStream<CLLocationCoordinate2D>
    var stopTracking: () -> Void
}

extension LocationClient: TestDependencyKey {
    static let testValue = LocationClient(
        authorizationStatus: { .authorizedWhenInUse },
        lastLocation: { nil },
        requestWhenInUse: { },
        requestAlways: { },
        startTracking: { AsyncStream { _ in } },
        stopTracking: { }
    )
}

extension LocationClient: DependencyKey {
    static var liveValue: LocationClient = {
        let manager = LiveLocationManager()
        return LocationClient(
            authorizationStatus: {
                manager.currentAuthorizationStatus
            },
            lastLocation: {
                manager.lastLocation
            },
            requestWhenInUse: {
                await manager.requestWhenInUse()
            },
            requestAlways: {
                await manager.requestAlways()
            },
            startTracking: {
                manager.startTracking()
            },
            stopTracking: {
                manager.stopTracking()
            }
        )
    }()
}

extension DependencyValues {
    var locationClient: LocationClient {
        get { self[LocationClient.self] }
        set { self[LocationClient.self] = newValue }
    }
}

/// CRIT-7 (audit 2026-05-17): rewritten for multi-consumer safety +
/// continuation-race safety.
///
/// Two bugs the previous version had:
///   1) `startTracking()` called `stopTracking()` which finished the
///      single shared continuation. If a second feature called
///      `startTracking()` while the chicken was iterating, the chicken's
///      for-await loop exited silently and the chicken stopped emitting
///      its position for the rest of the game.
///   2) `authorizationContinuation` was a single optional that callers
///      stomped on — two concurrent `requestAlways()` (or the same
///      caller's two-step .whenInUse → .always sequence racing with a
///      delegate fire) tripped the Swift "continuation called twice"
///      trap.
///
/// Fix:
///   1) Multiple continuations keyed by UUID. Each `startTracking()`
///      call returns a fresh AsyncStream; cancellation removes only
///      that one. CoreLocation's `startUpdatingLocation` is
///      ref-counted on the 0→1 transition; `stopUpdatingLocation`
///      fires on 1→0 (or on explicit `stopTracking()`, which still
///      tears down ALL consumers).
///   2) Authorization continuations are a FIFO queue. Every requester
///      appends a continuation; the delegate callback drains the
///      whole queue on the first resolved status. Concurrent callers
///      all wake on the same delegate fire instead of clobbering
///      each other's continuation slot.
///
/// Thread safety: the class is `@unchecked Sendable` because
/// CLLocationManager + its delegate run on the main thread by default
/// (we never reassign the delegate from anywhere else), but the API
/// surface can be called from any actor / Task. An NSLock guards
/// mutations to the continuations / queue so concurrent callers
/// can't corrupt the dictionaries.
private final class LiveLocationManager: NSObject, CLLocationManagerDelegate, @unchecked Sendable {
    private let locationManager = CLLocationManager()
    private let lock = NSLock()

    /// Active location stream consumers. Each `startTracking()` call
    /// inserts an entry; the stream's `onTermination` removes it. The
    /// manager's `startUpdatingLocation` is called on 0→1, stopped on
    /// 1→0 (or on `stopTracking()`).
    private var continuations: [UUID: AsyncStream<CLLocationCoordinate2D>.Continuation] = [:]

    /// FIFO of authorization-change waiters. Drained by
    /// `didChangeAuthorization` whenever the status is no longer
    /// `.notDetermined`.
    private var authorizationContinuations: [CheckedContinuation<Void, Never>] = []

    var currentAuthorizationStatus: CLAuthorizationStatus {
        locationManager.authorizationStatus
    }

    var lastLocation: CLLocationCoordinate2D? {
        locationManager.location?.coordinate
    }

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = AppConstants.locationMinDistanceMeters
        // Do NOT set `allowsBackgroundLocationUpdates = true` here — Apple
        // requires .authorizedAlways *before* the flag is flipped, otherwise
        // `startUpdatingLocation` crashes with an exception. The flag is
        // set in `refreshBackgroundCapability()` once authorization is
        // granted (see `requestAlways` + delegate callback).
        //
        // iOS otherwise pauses location updates when the device thinks
        // the user is stationary. During an active game we explicitly do
        // not want that — a Chicken hiding in one spot must still be
        // broadcasting heartbeats + position on radar-ping / jammer / etc.
        locationManager.pausesLocationUpdatesAutomatically = false
    }

    /// Enable `allowsBackgroundLocationUpdates` only when we hold `.authorizedAlways`.
    /// Setting the flag without that authorization crashes the app at
    /// `startUpdatingLocation` time. Without the flag, Info.plist's
    /// `UIBackgroundModes = location` is inert — CoreLocation stops
    /// delivering updates the moment the app is backgrounded, and the
    /// Chicken appears frozen on every Hunter's map.
    private func refreshBackgroundCapability() {
        let granted = locationManager.authorizationStatus == .authorizedAlways
        if locationManager.allowsBackgroundLocationUpdates != granted {
            locationManager.allowsBackgroundLocationUpdates = granted
        }
    }

    /// Queue a continuation that will be resumed by the next
    /// `didChangeAuthorization` callback (any non-`.notDetermined`
    /// status). Multiple concurrent waiters all wake on the same fire.
    private func waitForAuthorizationChange() async {
        await withCheckedContinuation { continuation in
            lock.lock()
            authorizationContinuations.append(continuation)
            lock.unlock()
        }
    }

    func requestWhenInUse() async {
        if locationManager.authorizationStatus != .notDetermined { return }
        // Trigger the system prompt (idempotent for queued callers — iOS
        // collapses duplicate prompts), then wait for the delegate.
        locationManager.requestWhenInUseAuthorization()
        await waitForAuthorizationChange()
    }

    func requestAlways() async {
        let status = locationManager.authorizationStatus
        if status == .authorizedAlways { return }
        if status == .notDetermined {
            // Must request "when in use" first before "always".
            locationManager.requestWhenInUseAuthorization()
            await waitForAuthorizationChange()
        }
        if locationManager.authorizationStatus == .authorizedWhenInUse {
            locationManager.requestAlwaysAuthorization()
            await waitForAuthorizationChange()
        }
    }

    func startTracking() -> AsyncStream<CLLocationCoordinate2D> {
        // Re-check background capability on every start: the user may have
        // upgraded the authorization in Settings between launches.
        refreshBackgroundCapability()

        let id = UUID()
        return AsyncStream { continuation in
            let shouldStartManager: Bool = {
                lock.lock(); defer { lock.unlock() }
                let firstConsumer = continuations.isEmpty
                continuations[id] = continuation
                return firstConsumer
            }()
            continuation.onTermination = { [weak self] _ in
                guard let self else { return }
                let shouldStopManager: Bool = {
                    self.lock.lock(); defer { self.lock.unlock() }
                    self.continuations.removeValue(forKey: id)
                    return self.continuations.isEmpty
                }()
                if shouldStopManager {
                    DispatchQueue.main.async {
                        self.locationManager.stopUpdatingLocation()
                    }
                }
            }
            if shouldStartManager {
                DispatchQueue.main.async { [weak self] in
                    self?.locationManager.startUpdatingLocation()
                }
            }
        }
    }

    /// Hard stop: end every active stream AND stop the manager. Callers
    /// expecting "stop just my stream" should rely on Task cancellation
    /// (which fires the AsyncStream's `onTermination` and removes only
    /// their consumer).
    func stopTracking() {
        let toFinish: [AsyncStream<CLLocationCoordinate2D>.Continuation] = {
            lock.lock(); defer { lock.unlock() }
            let all = Array(continuations.values)
            continuations.removeAll()
            return all
        }()
        for continuation in toFinish { continuation.finish() }
        DispatchQueue.main.async { [weak self] in
            self?.locationManager.stopUpdatingLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        let snapshot: [AsyncStream<CLLocationCoordinate2D>.Continuation] = {
            lock.lock(); defer { lock.unlock() }
            return Array(continuations.values)
        }()
        for continuation in snapshot {
            continuation.yield(location.coordinate)
        }
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        // Authorization just transitioned — this is the right hook to flip
        // `allowsBackgroundLocationUpdates` on/off. Upgrading from
        // .authorizedWhenInUse to .authorizedAlways mid-session must still
        // enable background tracking for the rest of the game.
        refreshBackgroundCapability()
        guard status != .notDetermined else { return }
        let waiters: [CheckedContinuation<Void, Never>] = {
            lock.lock(); defer { lock.unlock() }
            let snap = authorizationContinuations
            authorizationContinuations.removeAll()
            return snap
        }()
        for continuation in waiters { continuation.resume() }
    }
}
