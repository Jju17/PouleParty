//
//  EndGameCode.swift
//  PouleParty
//
//  Created by Julien Rahier on 19/03/2024.
//

import SwiftUI

struct EndGameCodeView: View {
    var foundCode: String

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Text("Show this code\nto the hunter")
                .font(.gameboy(size: 14))
                .multilineTextAlignment(.center)

            Text(foundCode)
                .font(.gameboy(size: 48))
                .kerning(8)

            Text("The hunter must enter this code to prove they found you!")
                .font(.system(size: 14))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Spacer()
        }
        .padding()
    }
}

#Preview {
    EndGameCodeView(foundCode: "4729")
}
