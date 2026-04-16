//
//  MapWarmUp.swift
//  PouleParty
//

import Foundation
import Metal
@preconcurrency import MapboxMaps

enum MapWarmUp {
    private static var hasWarmedUp = false

    /// Pre-warms the Metal device and Mapbox TileStore on a background thread.
    /// Safe to call multiple times — only runs once.
    static func warmUpIfNeeded() {
        guard !hasWarmedUp else { return }
        hasWarmedUp = true
        DispatchQueue.global(qos: .userInitiated).async {
            _ = MTLCreateSystemDefaultDevice()
            _ = TileStore.default
        }
    }
}
