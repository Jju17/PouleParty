//
//  LocationClient.swift
//  PouleParty
//
//  Created by Julien Rahier on 13/02/2026.
//

import ComposableArchitecture
import CoreLocation

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

private final class LiveLocationManager: NSObject, CLLocationManagerDelegate {
    private let locationManager = CLLocationManager()
    private var continuation: AsyncStream<CLLocationCoordinate2D>.Continuation?
    private var authorizationContinuation: CheckedContinuation<Void, Never>?

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

    func requestWhenInUse() async {
        let status = locationManager.authorizationStatus
        if status != .notDetermined { return }
        await withCheckedContinuation { continuation in
            self.authorizationContinuation?.resume()
            self.authorizationContinuation = continuation
            locationManager.requestWhenInUseAuthorization()
        }
    }

    func requestAlways() async {
        let status = locationManager.authorizationStatus
        if status == .authorizedAlways { return }
        if status == .notDetermined {
            // Must request "when in use" first before "always"
            await withCheckedContinuation { continuation in
                self.authorizationContinuation?.resume()
                self.authorizationContinuation = continuation
                locationManager.requestWhenInUseAuthorization()
            }
        }
        // Now request always if we have at least "when in use"
        if locationManager.authorizationStatus == .authorizedWhenInUse {
            await withCheckedContinuation { continuation in
                self.authorizationContinuation?.resume()
                self.authorizationContinuation = continuation
                locationManager.requestAlwaysAuthorization()
            }
        }
    }

    func startTracking() -> AsyncStream<CLLocationCoordinate2D> {
        stopTracking()
        // Re-check background capability on every start: the user may have
        // upgraded the authorization in Settings between launches.
        refreshBackgroundCapability()

        return AsyncStream { continuation in
            self.continuation = continuation
            continuation.onTermination = { [weak self] _ in
                self?.locationManager.stopUpdatingLocation()
            }
            self.locationManager.startUpdatingLocation()
        }
    }

    func stopTracking() {
        continuation?.finish()
        continuation = nil
        locationManager.stopUpdatingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        continuation?.yield(location.coordinate)
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        // Authorization just transitioned — this is the right hook to flip
        // `allowsBackgroundLocationUpdates` on/off. Upgrading from
        // .authorizedWhenInUse to .authorizedAlways mid-session must still
        // enable background tracking for the rest of the game.
        refreshBackgroundCapability()
        if status != .notDetermined {
            authorizationContinuation?.resume()
            authorizationContinuation = nil
        }
    }
}
