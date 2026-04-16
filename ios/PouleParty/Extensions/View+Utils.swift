//
//  View+Utils.swift
//  PouleParty
//

import SwiftUI
import UIKit

extension View {
    /// Keeps the screen awake while the view is on screen.
    func idleTimerDisabled() -> some View {
        self
            .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
            .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }
}
