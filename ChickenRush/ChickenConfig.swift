//
//  ChickenConfig.swift
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
        var endDate: Date = .now.addingTimeInterval(3900)
        var startDate: Date = .now.addingTimeInterval(300)
        var latitude: String = ""
        var longitude: String = ""
        var radiusIntervalUpdate: Double = 2
        var gameMod: GameMod = .followTheChicken
        var radiusSize: Double = 1500
        var radiusDecline: Double = 100

        enum GameMod: String, CaseIterable {
            case followTheChicken = "Follow the chicken 🐔"
            case stayInTheZone = "Stay in tha zone 📍"
        }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case goBackButtonTriggered
        case startGameButtonTapped
        case startGameTriggered(Game)
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .goBackButtonTriggered:
                return .none
            case .startGameButtonTapped:
                return .run { [state = state] send in
                    let lat: Double = Double(state.latitude) ?? 50.8466
                    let long: Double = Double(state.longitude) ?? 4.3528

                    let newGame = Game(
                        id: UUID().uuidString,
                        name: "Partie 1",
                        numberOfPlayers: 10,
                        radiusIntervalUpdate: Int(state.radiusIntervalUpdate),
                        startTimestamp: Timestamp(date: state.startDate),
                        endTimestamp: Timestamp(date: state.endDate),
                        initialCoordinates: GeoPoint(latitude: lat, longitude: long),
                        initialRadius: Int(state.radiusSize), 
                        radiusDeclinePerUpdate: 100
                    )

                    do {
                        try await apiClient.setConfig(newGame)
                        await send(.startGameTriggered(newGame))
                    } catch {
                        print("Error adding document: \(error)")
                    }
                }
            case .startGameTriggered:
                return .none

            }
        }
    }
}


struct ChickenConfigView: View {
    @Bindable var store: StoreOf<ChickenConfigFeature>

    var body: some View {
        NavigationStack {
            Form {
                DatePicker(selection: $store.startDate, in: .now.addingTimeInterval(60)...){
                    Text("Start at")
                }
                .datePickerStyle(.compact)
                DatePicker(selection: $store.endDate, in: store.startDate.addingTimeInterval(300)..., displayedComponents: .hourAndMinute){
                    Text("End at")
                }
                .datePickerStyle(.compact)
                HStack {
                    Text("Latitude")
                    Spacer()
                    TextField("50.8466", text: $store.latitude)
                        .multilineTextAlignment(.trailing)
                        .frame(width:100)
                }
                HStack {
                    Text("Longitude")
                    Spacer()
                    TextField("4.3528", text: $store.longitude)
                        .multilineTextAlignment(.trailing)
                        .frame(width:100)
                }
                VStack(alignment: .leading) {
                    HStack {
                        Text("Radius interval update")
                        Spacer()
                        Text("\(Int(self.store.radiusIntervalUpdate)) minutes")
                    }
                    Slider(value: self.$store.radiusIntervalUpdate, in: 1...60, step: 1)
                }
                VStack(alignment: .leading) {
                    HStack {
                        Text("Radius size")
                        Spacer()
                        Text("\(Int(self.store.radiusSize)) meters")
                    }
                    Slider(value: self.$store.radiusSize, in: 500...2000, step: 100)
                }
                VStack(alignment: .leading) {
                    HStack {
                        Text("Radius decline")
                        Spacer()
                        Text("\(Int(self.store.radiusDecline)) meters")
                    }
                    Slider(value: self.$store.radiusDecline, in: 50...1000, step: 10)
                }
                Button("Start game") {
                    store.send(.startGameButtonTapped)
                }
            }
            .navigationTitle("Game Settings")
        }

    }
}

#Preview {
    ChickenConfigView(store: Store(initialState: ChickenConfigFeature.State()) {
        ChickenConfigFeature()
    })
}
