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
}

extension LocationClient: DependencyKey {
    static var liveValue = LocationClient(
        locationServicesEnabled: {
            return true
        },
        requestAlwaysAuthorization: {

        },
        requestLocation: {

        },
        startUpdatingLocation: {

        },
        stopUpdatingLocation: {

        }
    )
}

extension DependencyValues {
    var locationClient: LocationClient {
        get { self[LocationClient.self] }
        set { self[LocationClient.self] = newValue }
    }
}
