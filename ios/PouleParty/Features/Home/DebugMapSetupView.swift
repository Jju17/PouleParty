//
//  DebugMapSetupView.swift
//  PouleParty
//
//  Long-press-on-Create-Party easter egg wrapper. Hosts the regular
//  ChickenMapConfigView (from GameCreation) so the user can place the
//  start + final zone pins and pick a radius, then a single "Launch
//  Preview" button commits the game and drops onto the debug chicken
//  map with every shrunk circle pre-rendered.
//

import ComposableArchitecture
import SwiftUI

struct DebugMapSetupView: View {
    let store: StoreOf<ChickenMapConfigFeature>
    let onLaunch: () -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationStack {
            ChickenMapConfigView(store: store)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { onCancel() }
                    }
                    ToolbarItem(placement: .principal) {
                        Text("Debug Preview")
                            .font(.gameboy(size: 10))
                            .foregroundStyle(Color.CROrange)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Launch") { onLaunch() }
                            .font(.gameboy(size: 10))
                            .foregroundStyle(Color.CROrange)
                    }
                }
        }
    }
}
