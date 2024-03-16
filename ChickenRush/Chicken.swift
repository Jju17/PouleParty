//
//  Chicken.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import SwiftUI

@Reducer
struct ChickenConfigFeature {

    @ObservableState
    struct State {
        var latitude: String = "50.8466"
        var longitude: String = "4.3528"
        var gameMod: GameMod = .followTheChicken
        let db = Firestore.firestore()

        enum GameMod: String, CaseIterable {
            case followTheChicken = "Follow the chicken 🐔"
            case stayInTheZone = "Stay in tha zone 📍"
        }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case setCoordinatesButtonTapped
        case wrongCoordinatesTriggered
    }

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .setCoordinatesButtonTapped:
                return .run { [db = state.db, lat = state.latitude, long = state.longitude] send in
                    guard let lat = Double(lat), let long = Double(long) 
                    else { return await send(.wrongCoordinatesTriggered) }

                    do {
                        let ref = try await db.collection("locations").addDocument(data: [
                            "location": GeoPoint(latitude: lat, longitude: long),
                      ])
                      print("Document added with ID: \(ref.documentID)")
                    } catch {
                      print("Error adding document: \(error)")
                    }
                }
            case .wrongCoordinatesTriggered:
                return .none
            }
        }
    }

}


struct ChickenConfigView: View {
    @Bindable var store: StoreOf<ChickenConfigFeature>

    var body: some View {
        Form {
            TextField("latitude", text: $store.latitude)
            TextField("longitude", text: $store.longitude)

            Button("Set coordinates") {
                store.send(.setCoordinatesButtonTapped)
            }
        }
    }
}

#Preview {
    ChickenConfigView(store: Store(initialState: ChickenConfigFeature.State()) {
        ChickenConfigFeature()
    })
}
