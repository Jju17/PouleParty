import CoreLocation
import Foundation

private let earthRadiusMeters = 6_371_000.0

/// Great-circle distance, identical on iOS, Android and the server.
func distanceMeters(_ lat1: Double, _ lng1: Double, _ lat2: Double, _ lng2: Double) -> Double {
    let toRad = Double.pi / 180
    let dLat = (lat2 - lat1) * toRad
    let dLng = (lng2 - lng1) * toRad
    let a = pow(sin(dLat / 2), 2) + cos(lat1 * toRad) * cos(lat2 * toRad) * pow(sin(dLng / 2), 2)
    return 2 * earthRadiusMeters * asin(min(1, a.squareRoot()))
}

func distanceMeters(_ from: CLLocationCoordinate2D, _ to: CLLocationCoordinate2D) -> Double {
    distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)
}
