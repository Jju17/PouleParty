//
//  LocationManager.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import CoreLocation
import FirebaseFirestore

class LocationManager: NSObject, CLLocationManagerDelegate {
    private var locationManager: CLLocationManager?
    private let db = Firestore.firestore()
    private var lastUpdateTime: Date = .now

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
            locationManager?.startUpdatingLocation()
        case .denied, .restricted:
            print("Location access denied")
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let location = locations.last,
           Date.now >= lastUpdateTime.addingTimeInterval(30)
        {
            do {
                let chickenLocation = ChickenLocation(
                    location: GeoPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude),
                    timestamp: Timestamp(date: .now)
                )
                let chickenLocationRef = self.db.collection("chickenLocations").document()
                try chickenLocationRef.setData(from: chickenLocation)
                self.lastUpdateTime = .now
                print("Updated location successfully!")
            } catch {
              print("Error adding document: \(error)")
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Handle error
        print("Location manager failed with error: \(error)")
    }
}

