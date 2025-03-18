//
//  LocationManager.swift
//  ChickenRush
//
//  Created by Julien Rahier on 14/04/2024.
//

import Combine
import ComposableArchitecture
import CoreLocation
import FirebaseFirestore

final class LocationManager: NSObject, CLLocationManagerDelegate  {

    private var locationManager: CLLocationManager = CLLocationManager()
    private var timer: Timer?
    private var actualLocation: CLLocation?
    private var firestore = Firestore.firestore()

    private var isLocationServicesAuthorized: Bool {
        return CLLocationManager().authorizationStatus == .authorizedWhenInUse || CLLocationManager().authorizationStatus == .authorizedAlways
    }

    static let shared = LocationManager()

    override init() {
        super.init()
        self.locationManager.delegate = self
        if self.isLocationServicesAuthorized {
            self.startUpdatingUserLocation()
        }
    }

    //MARK: - Public

    public func askForLocationServicesAuthorization() {
        self.locationManager.requestAlwaysAuthorization()
    }

    public func stopUpdatingLocation() {
        self.timer?.invalidate()
        self.timer = nil
    }

    //MARK: Poulet

    public func updateLocation(every seconds: TimeInterval) {
        self.timer?.invalidate()
        self.timer = Timer.scheduledTimer(withTimeInterval: seconds, repeats: true) { [weak self] _ in
            self?.updateFirestoreLocation()
        }
    }

    //MARK: - Private

    private func startUpdatingUserLocation() {
        self.locationManager.desiredAccuracy = kCLLocationAccuracyBest
        self.locationManager.startUpdatingLocation()
    }

    private func updateFirestoreLocation() {
        guard let location = self.actualLocation else {
            print("Actual location is not available.")
            return
        }

        // Convert CLLocation to Firestore-friendly format
        let locationData = LocationData(
            latitude: location.coordinate.latitude,
            longiture: location.coordinate.longitude,
            timestamp: Timestamp(date: location.timestamp)
        )

        do {
            try self.firestore.collection("chickenLocations").document().setData(from: locationData)
            print("Location successfully updated in Firestore!")
        } catch {
            print("Error updating Firestore location: \(error.localizedDescription)")
        }
    }

    //MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if self.isLocationServicesAuthorized {
            self.startUpdatingUserLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        print(location)
        self.actualLocation = location
    }
}
