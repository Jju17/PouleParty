//
//  RoadSnapService.swift
//  PouleParty
//
//  Snaps coordinates to the nearest walkable road using the Mapbox
//  Directions API. Each waypoint is independently snapped to the
//  nearest road segment. Falls back to the original position when
//  the API is unreachable or no snap is found.
//

import CoreLocation
import Foundation
import os

private let logger = Logger(category: "RoadSnapService")

enum RoadSnapService {

    /// Snaps an array of coordinates to the nearest walkable roads.
    /// Processes each point individually via the Directions API to
    /// guarantee independent snapping (no route dependency).
    static func snapToRoads(_ coordinates: [CLLocationCoordinate2D]) async -> [CLLocationCoordinate2D] {
        guard !coordinates.isEmpty,
              let token = mapboxAccessToken else {
            return coordinates
        }

        return await withTaskGroup(of: (Int, CLLocationCoordinate2D).self) { group in
            for (index, coord) in coordinates.enumerated() {
                group.addTask {
                    let snapped = await snapSinglePoint(coord, accessToken: token)
                    return (index, snapped)
                }
            }

            var result = coordinates
            for await (index, snapped) in group {
                result[index] = snapped
            }
            return result
        }
    }

    // MARK: - Private

    /// Snaps a single coordinate to the nearest walkable road.
    /// Uses the Directions API with a tiny offset point so the API
    /// returns the snapped waypoint location.
    private static func snapSinglePoint(
        _ coordinate: CLLocationCoordinate2D,
        accessToken: String
    ) async -> CLLocationCoordinate2D {
        // Create a second point ~10m north so the Directions API has a valid route
        let offsetLat = coordinate.latitude + 0.0001 // ~11m north
        let coordString = "\(coordinate.longitude),\(coordinate.latitude);\(coordinate.longitude),\(offsetLat)"

        let urlString = "https://api.mapbox.com/directions/v5/mapbox/walking/\(coordString)"
            + "?access_token=\(accessToken)"
            + "&overview=false"
            + "&steps=false"

        guard let url = URL(string: urlString) else { return coordinate }

        do {
            let (data, response) = try await URLSession.shared.data(from: url)

            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                logger.warning("Directions API returned non-200 status")
                return coordinate
            }

            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let waypoints = json["waypoints"] as? [[String: Any]],
                  let firstWaypoint = waypoints.first,
                  let location = firstWaypoint["location"] as? [Double],
                  location.count == 2 else {
                return coordinate
            }

            let snapped = CLLocationCoordinate2D(latitude: location[1], longitude: location[0])

            // Reject snaps that moved the point too far (> 200m) — likely a data issue
            let original = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
            let snappedLoc = CLLocation(latitude: snapped.latitude, longitude: snapped.longitude)
            if original.distance(from: snappedLoc) > 200 {
                logger.info("Snap moved point > 200m, keeping original")
                return coordinate
            }

            return snapped

        } catch {
            logger.warning("Road snap failed: \(error.localizedDescription)")
            return coordinate
        }
    }

    private static var mapboxAccessToken: String? {
        Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String
    }
}
