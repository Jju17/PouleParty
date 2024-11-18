//
//  Selection.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import Combine
import CoreLocation
import SwiftUI
import AVFAudio

@Reducer
struct SelectionFeature {

    @ObservableState
    struct State {
        @Presents var destination: Destination.State?
        var location: CLLocation?
    }

    enum Action {
        case chickenButtonTapped
        case destination(PresentationAction<Destination.Action>)
        case dismissChickenConfig
        case goToChickenConfigTriggered
        case goToChickenMapTriggered(Game)
        case hunterButtonTapped
        case locationAuthorizationStatus(CLAuthorizationStatus)
        case locationError(String)
        case locationResponse(CLLocation)
        case locationTask
    }

    @Reducer
    struct Destination {
        enum State {
            case alert(AlertState<Action.Alert>)
            case chickenConfig(ChickenConfigFeature.State)
        }

        enum Action {
            case alert(Alert)
            case chickenConfig(ChickenConfigFeature.Action)

            enum Alert {
                case addAlertHere
            }
        }

        var body: some ReducerOf<Self> {
            Scope(state: \.chickenConfig, action: \.chickenConfig) {
                ChickenConfigFeature()
            }
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.locationClient) var locationClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .chickenButtonTapped:
                return .run { send in
                    let game = await apiClient.getConfig()

                    guard game != nil
                    else {
                        await send(.goToChickenConfigTriggered)
                        return
                    }

                    if game!.endDate > .now {
                        await send(.goToChickenMapTriggered(game!))
                    } else if game!.endDate < .now {
                        try await apiClient.deleteConfig()
                        await send(.goToChickenConfigTriggered)
                    }
                }
            case let .destination(.presented(.chickenConfig(.startGameTriggered(game)))):
                state.destination = nil
                return .run { send in
                    await send(.goToChickenMapTriggered(game))
                }
            case .destination:
                return .none
            case .dismissChickenConfig:
                state.destination = nil
                return .none
            case .goToChickenConfigTriggered:
                state.destination = .chickenConfig(
                    ChickenConfigFeature.State(game:Shared(Game(id: UUID().uuidString)))
                )
                return .none
            case .goToChickenMapTriggered:
                return .none
            case .hunterButtonTapped:
                return .none
            case let .locationAuthorizationStatus(status):
                switch status {
                case .notDetermined:
                    return .run { _ in
                        self.locationClient.requestAlwaysAuthorization()
                    }
                case .restricted:
                    // TODO: show an alert
                    break
                case .denied:
                    // TODO: show an alert
                    break
                case .authorizedAlways, .authorizedWhenInUse:
                    return .run { _ in
                        self.locationClient.requestAlwaysAuthorization()
                    }
                @unknown default:
                    break
                }
                return .none
            case let .locationError(error):
                _ = error
                return .none
            case let .locationResponse(location):
                state.location = location
                print("\nLocation Response: ", state.location ?? CLLocation())
                return .none
            case .locationTask:
                self.locationClient.requestAlwaysAuthorization()
                self.locationClient.startUpdatingLocation()
                return .run { _ in
                    for await value in self.locationClient.delegate {
                        print("JR triggered: \(value) ")
                    }
                }
            }
        }
        .ifLet(\.$destination, action: \.destination) {
            Destination()
        }
    }
}

extension SelectionFeature {
    private func mapEventToAction(action: LocationClient.DelegateAction) -> SelectionFeature.Action {
        print("JR receive action: \(action)")
        switch action {
        case let .didChangeAuthorization(status):
            print("JR Status: \(status)")
            return { Action.locationAuthorizationStatus(status) }()
        case let .didUpdateLocations(locations):
            if let location = locations.last {
                return { Action.locationResponse(location) }()
            } else {
                return { Action.locationResponse(CLLocation()) }()
            }

        case let .didFailWithError(error):
            return { Action.locationError(error) }()
        }
    }
}

struct SelectionView: View {
    @Bindable var store: StoreOf<SelectionFeature>
    private let selectionChickenTip = SelectChickenTip()
    private let selectionHunterTip = SelectHunterTip()
    @State private var isVisible = true
    @State private var audioPlayer: AVAudioPlayer?

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(alignment: .center, spacing: 0) {
                Spacer()
                VStack(alignment: .center, spacing: 10) {
                    Image("logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)

                    Button {
                        self.store.send(.hunterButtonTapped)
                    } label: {
                        Text("START")
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .font(.gameboy(size: 22))
                            .padding()
                            .foregroundStyle(.black)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(.black, lineWidth: 4)
                            )
                            .opacity(isVisible ? 1 : 0)
                            .animation(
                                Animation.easeInOut(duration: 0.5)
                                    .repeatForever(autoreverses: true)
                            )
                            .onAppear {
                                isVisible.toggle()
                            }
                    }
                    .frame(width: 200, height: 50)

                    Text("Press start to play")
                        .font(.gameboy(size: 12))
                        .foregroundStyle(Color.black)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.CRBeige)

            Button {
                // Action for bottom-right button
            } label: {
                Text("C'est moi la poule")
                    .padding()
                    .foregroundColor(.black)
                    .font(.gameboy(size: 8))
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(.black, lineWidth: 1.5)
                    )
            }
            .padding()
        }
        .onAppear {
            self.playSound()
        }
        .sheet(
            item: $store.scope(
                state: \.destination?.chickenConfig,
                action: \.destination.chickenConfig
            )
        ) { store in
            NavigationStack {
                ChickenConfigView(store: store)
                    .toolbar {
                        ToolbarItem {
                            Button {
                                self.store.send(.dismissChickenConfig)
                            } label: {
                                Image(systemName: "xmark")
                            }

                        }
                    }
            }
        }
    }

    private func playSound() {
        guard let path = Bundle.main.path(forResource: "background-music", ofType: "mp3") else { return }
        let url = URL(fileURLWithPath: path)

        do {
            self.audioPlayer = try AVAudioPlayer(contentsOf: url)
            self.audioPlayer?.numberOfLoops = -1
            self.audioPlayer?.volume = 0.1
            self.audioPlayer?.play()
        } catch {
            print("Failed to play sound: \(error.localizedDescription)")
        }
    }
}

#Preview {
    SelectionView(
        store:
            Store(initialState: SelectionFeature.State()) {
                SelectionFeature()
            }
    )
}
