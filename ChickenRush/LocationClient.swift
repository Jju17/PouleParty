//
//  LocationClient.swift
//  ChickenRush
//
//  Created by Julien Rahier on 14/04/2024.
//

import Combine
import ComposableArchitecture
import CoreLocation

struct LocationClient {
    var locationServicesEnabled: () -> Bool
    var requestAlwaysAuthorization: () -> Void
    var requestLocation: () -> Void
    var startUpdatingLocation: () -> Void
    var stopUpdatingLocation: () -> Void
    var delegate: AsyncStream<DelegateAction>

    enum DelegateAction {
        case didChangeAuthorization(CLAuthorizationStatus)
        case didUpdateLocations([CLLocation])
        case didFailWithError(String)
    }
}

extension LocationClient: DependencyKey {
    static var liveValue: LocationClient {
        class Delegate: NSObject, CLLocationManagerDelegate {
            var continuation: AsyncStream<DelegateAction>.Continuation?

            lazy var stream: AsyncStream<DelegateAction> = {
                AsyncStream { continuation in
                    self.continuation = continuation
                }
            }()

            init(continuation: AsyncStream<DelegateAction>.Continuation? = nil) {
                self.continuation = continuation
            }

            deinit {
                continuation?.finish()
            }

            func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
                self.continuation?.yield(.didChangeAuthorization(status))

            }

            func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
                self.continuation?.yield(.didUpdateLocations(locations))
            }

            func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
                self.continuation?.yield(.didFailWithError(error.localizedDescription))
            }
        }

        let locationManager = CLLocationManager()
        let delegate = Delegate()
        locationManager.delegate = delegate

        return LocationClient(
            locationServicesEnabled: CLLocationManager.locationServicesEnabled,
            requestAlwaysAuthorization: { locationManager.requestAlwaysAuthorization() },
            requestLocation: { locationManager.requestLocation() },
            startUpdatingLocation: { locationManager.startUpdatingLocation() },
            stopUpdatingLocation: { locationManager.stopUpdatingLocation() },
            delegate: delegate.stream
        )
    }
}

extension DependencyValues {
    var locationClient: LocationClient {
        get { self[LocationClient.self] }
        set { self[LocationClient.self] = newValue }
    }
}
