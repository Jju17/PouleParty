//
//  LocationManager.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import CoreLocation
import CoreLocationUI

class LocationManager: NSObject, CLLocationManagerDelegate {
    var locationManager: CLLocationManager?

    override init() {
        super.init()
        locationManager = CLLocationManager()
        locationManager?.delegate = self
        locationManager?.desiredAccuracy = kCLLocationAccuracyBest
        locationManager?.allowsBackgroundLocationUpdates = true
        locationManager?.requestAlwaysAuthorization()
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        switch status {
        case .authorizedWhenInUse, .authorizedAlways:
            print("JR: authorizedAlways")
            locationManager?.startUpdatingLocation()
        case .denied, .restricted:
            // Handle case where user denied or restricted access to location
            // You may want to prompt the user to enable location services in settings
            print("Location access denied")
            print("JR: Location access denied")
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // Handle updated locations
        if let location = locations.last {
            print("Updated location: \(location)")
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Handle error
        print("Location manager failed with error: \(error)")
    }
}

