//
//  ChickenRushApp.swift
//  ChickenRush
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import Firebase
import SwiftUI
import Foundation
import TipKit

@main
struct ChickenRushApp: App {

    init() {
        FirebaseApp.configure()
        try? Tips.configure([
            .displayFrequency(.immediate)
        ])
    }

    var body: some Scene {
        WindowGroup {
            AppView(
                store: Store(
                    initialState: AppFeature.State.selection(SelectionFeature.State())
                ) {
                    AppFeature()
                }
            )
        }
    }
}
