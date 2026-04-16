//
//  MapTopBar.swift
//  PouleParty
//
//  Shared top bar for map screens.
//

import SwiftUI

struct MapTopBar: View {
    let title: String
    let subtitle: String
    let gradient: LinearGradient
    let onInfoTapped: () -> Void

    var body: some View {
        HStack {
            Spacer()
            VStack(spacing: 2) {
                BangerText(title, size: 20)
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.3), radius: 2, y: 1)
                Text(subtitle)
                    .font(.gameboy(size: 10))
                    .foregroundStyle(.white.opacity(0.8))
            }
            Spacer()
            Button {
                onInfoTapped()
            } label: {
                Image(systemName: "info.circle")
                    .font(.system(size: 20))
                    .foregroundStyle(.white.opacity(0.8))
            }
            .accessibilityLabel("Game info")
            .padding(.trailing, 4)
        }
        .padding()
        .background(gradient)
    }
}
