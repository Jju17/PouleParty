//
//  GameStartCountdownOverlay.swift
//  PouleParty
//

import SwiftUI

struct GameStartCountdownOverlay: View {
    let countdownNumber: Int?
    let countdownText: String?

    var body: some View {
        ZStack {
            if let number = countdownNumber {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()

                Text("\(number)")
                    .font(.gameboy(size: 80))
                    .foregroundStyle(Color.CROrange)
                    .id(number)
                    .transition(.scale.combined(with: .opacity))
                    .animation(.easeOut(duration: 0.3), value: number)
            } else if let text = countdownText {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()

                Text(text)
                    .font(.banger(size: 48))
                    .foregroundStyle(Color.CROrange)
                    .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: countdownNumber)
        .animation(.easeInOut(duration: 0.3), value: countdownText)
    }
}
