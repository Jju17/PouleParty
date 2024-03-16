//
//  MapView.swift
//  ChickenRush
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import MapKit
import SwiftUI

@Reducer
struct MapFeature {

    @ObservableState
    struct State {
        var locationManager: LocationManager?
        var radius: Int = 1500
        var nextRadiusUpdate: Date = Date.now.addingTimeInterval(1400)
        var timer: Timer?
    }

    enum Action: BindableAction {
        case addAMinuteButtonTapped
        case binding(BindingAction<State>)
        case onAppear
        case updateTimer
        case lowerRadius
    }

    @Dependency(\.continuousClock) var clock

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .addAMinuteButtonTapped:
                state.nextRadiusUpdate = state.nextRadiusUpdate.addingTimeInterval(60)
                return .none
            case .binding:
                return .none
            case .onAppear:
                state.locationManager = LocationManager()
                return .none
            case .lowerRadius:
                return .run { send in
                    await send(.updateTimer)
                }
            case .updateTimer:
                if state.radius > 100 {
                    state.radius -= 100
                } else {
                    state.radius = 1000
                }
                return .none
            }
        }
    }
}

struct MapView: View {
    @Bindable var store: StoreOf<MapFeature>

    var body: some View {
        Map {
            MapCircle(center: .clash, radius: CLLocationDistance(self.store.radius))
                    .foregroundStyle(.green.opacity(0.5))
                    .mapOverlayLevel(level: .aboveRoads)
            UserAnnotation()
        }
        .mapControls {
            MapUserLocationButton()
            MapCompass()
            MapScaleView()
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Radius : \(self.store.radius)m")
                    CountdownView(nextRadiusUpdate: self.$store.nextRadiusUpdate)
                }
                Spacer()
                Button {
                    self.store.send(.lowerRadius)
                } label: {
                    ZStack {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(.blue)
                        Image(systemName: "minus.circle.fill")
                            .resizable()
                            .scaledToFit()
                            .foregroundStyle(.white)
                            .frame(width: 20, height: 20)

                    }
                }
                .frame(width: 45, height: 40)
                Button {
                    self.store.send(.addAMinuteButtonTapped)
                } label: {
                    ZStack {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(.blue)
                        Image("Bar")
                            .resizable()
                            .scaledToFit()
                            .foregroundStyle(.white)
                            .frame(width: 25, height: 25)

                    }
                }
                .frame(width: 45, height: 40)


            }
            .padding()
            .background(.thinMaterial)
        }
        .onAppear {
            self.store.send(.onAppear)
        }
    }
}

extension CLLocationCoordinate2D {
    static let clash = CLLocationCoordinate2D(latitude: 50.829700, longitude: 4.369442)
}

#Preview {
    MapView(store: Store(initialState: MapFeature.State()) {
        MapFeature()
    })
}
