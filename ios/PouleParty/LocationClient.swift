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
        locationManager.distanceFilter = 10 // Only fire when moved ≥ 10 meters
    }

    func requestWhenInUse() async {
        let status = locationManager.authorizationStatus
        if status != .notDetermined { return }
        await withCheckedContinuation { continuation in
            self.authorizationContinuation = continuation
            locationManager.requestWhenInUseAuthorization()
        }
    }

    func requestAlways() async {
        let status = locationManager.authorizationStatus
        if status == .authorizedAlways { return }
        if status == .authorizedWhenInUse {
            await withCheckedContinuation { continuation in
                self.authorizationContinuation = continuation
                locationManager.requestAlwaysAuthorization()
            }
        }
    }

    func startTracking() -> AsyncStream<CLLocationCoordinate2D> {
        stopTracking()

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
        if status != .notDetermined {
            authorizationContinuation?.resume()
            authorizationContinuation = nil
        }
    }
}
