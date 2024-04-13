//
//  LocationManager.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import CoreLocation
import Dependencies
import MapKit

final class LocationManager: NSObject, ObservableObject {
    @Dependency(\.apiClient) var apiClient
    var location: CLLocationCoordinate2D?
    private var lastUpdateTime: Date = .now
    private var locationManager: CLLocationManager = CLLocationManager()
    private var isTrackingActive: Bool = false
    private var updatingMethod: TrackingMethod = .alwaysUpdating

    enum TrackingMethod {
        case alwaysUpdating, updatingOnce
    }

    override init() {
        super.init()
        self.locationManager.delegate = self
        self.locationManager.desiredAccuracy = kCLLocationAccuracyBest
        self.locationManager.allowsBackgroundLocationUpdates = true
        self.locationManager.requestAlwaysAuthorization()
    }

    convenience init(isTrackingActive: Bool, updatingMethod: TrackingMethod) {
        self.init()
        self.isTrackingActive = isTrackingActive
        self.updatingMethod = updatingMethod
    }

    private func updateChickenLocation(with location: CLLocation) {
        do {
            try apiClient.setChickenLocation(location.coordinate)
            self.lastUpdateTime = .now
            print("Updated location successfully!")
        } catch {
            print("Error adding document: \(error)")
        }
    }

    private func startTrackingLocation() {
        switch self.updatingMethod {
        case .alwaysUpdating:
            self.locationManager.startUpdatingLocation()
        case .updatingOnce:
            self.locationManager.stopUpdatingLocation()
            self.locationManager.requestLocation()
        }
    }
}

extension LocationManager: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        switch status {
        case .authorizedWhenInUse, .authorizedAlways:
            self.startTrackingLocation()
        case .denied, .restricted:
            print("Location access denied")
        default:
            break
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager.authorizationStatus == .authorizedWhenInUse ||
              manager.authorizationStatus == .authorizedAlways
        else { return }
        self.startTrackingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        self.location = locations.last.map { $0.coordinate }

        if self.isTrackingActive,
           let location = locations.last,
           Date.now >= lastUpdateTime.addingTimeInterval(30)
        {
            self.updateChickenLocation(with: location)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location manager failed with error: \(error)")
    }
}

