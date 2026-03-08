//
//  RoadSnapService.swift
//  PouleParty
//
//  Snaps coordinates to the nearest walkable road using the Mapbox
//  Map Matching API.  Keeps original position when the API is unreachable
//  or no match is found.
//

import CoreLocation
import Foundation

enum RoadSnapService {

    /// Snaps an array of coordinates to the nearest walkable roads.
    /// Returns an array of the same length; un-matchable points keep
    /// their original position.
    static func snapToRoads(_ coordinates: [CLLocationCoordinate2D]) async -> [CLLocationCoordinate2D] {
        guard coordinates.count >= 2 else {
            // Map Matching API requires at least 2 coordinates.
            // For a single point, duplicate it so we can still snap.
            if let single = coordinates.first {
                let pair = await snapToRoads([single, single])
                return [pair[0]]
            }
            return coordinates
        }

        guard let token = mapboxAccessToken else { return coordinates }

        let coordString = coordinates
            .map { "\($0.longitude),\($0.latitude)" }
            .joined(separator: ";")

        let radiuses = coordinates.map { _ in "50" }.joined(separator: ";")

        let urlString = "https://api.mapbox.com/matching/v5/mapbox/walking/\(coordString)"
            + "?access_token=\(token)"
            + "&radiuses=\(radiuses)"
            + "&steps=false"
            + "&overview=false"
            + "&geometries=geojson"

        guard let url = URL(string: urlString) else { return coordinates }

        do {
            let (data, response) = try await URLSession.shared.data(from: url)

            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                return coordinates
            }

            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let tracepoints = json["tracepoints"] as? [Any?] else {
                return coordinates
            }

            var result = coordinates
            for (index, tracepoint) in tracepoints.enumerated() {
                guard index < result.count,
                      let tp = tracepoint as? [String: Any],
                      let location = tp["location"] as? [Double],
                      location.count == 2 else {
                    continue
                }
                result[index] = CLLocationCoordinate2D(latitude: location[1], longitude: location[0])
            }
            return result

        } catch {
            return coordinates
        }
    }

    // MARK: - Private

    private static var mapboxAccessToken: String? {
        Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String
    }
}
