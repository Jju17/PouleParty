//
//  MapCompassButton.swift
//  PouleParty
//
//  Shared compass button for map screens.
//

import MapboxMaps
import SwiftUI

struct MapCompassButton: View {
    let mapCircle: CircleOverlay?
    let mapBearing: Double
    @Binding var viewport: Viewport

    var body: some View {
        Button {
            withViewportAnimation(.default(maxDuration: 0.5)) {
                if let circle = mapCircle {
                    viewport = .camera(
                        center: circle.center,
                        zoom: zoomForRadius(circle.radius, latitude: circle.center.latitude),
                        bearing: 0
                    )
                }
            }
        } label: {
            Image(systemName: "location.north.fill")
                .rotationEffect(.degrees(-mapBearing))
                .frame(width: 40, height: 40)
                .background(Color.surface)
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
        }
        .padding(.trailing, 8)
        .padding(.top, 8)
    }
}
