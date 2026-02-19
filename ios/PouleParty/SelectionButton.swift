//
//  SelectionButton.swift
//  PouleParty
//
//  Created by Julien Rahier on 03/04/2024.
//

import SwiftUI

struct SelectionButton: View {
    var titleKey: LocalizedStringKey
    var color: Color
    var lineWidth: CGFloat
    var action: () -> Void

    init(_ titleKey: LocalizedStringKey, color: Color = .black, lineWidth: CGFloat = 2.0, action: @escaping () -> Void) {
        self.titleKey = titleKey
        self.color = color
        self.action = action
        self.lineWidth = lineWidth
    }

    var body: some View {
        Button(action: self.action) {
            Text(self.titleKey)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .font(.gameboy(size: 20))
                .padding()
                .foregroundStyle(self.color)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(self.color, lineWidth: self.lineWidth)
                )
        }
    }
}

#Preview {
    ZStack {
        SelectionButton(" Sign up ") {

        }
        .frame(width: 300, height: 90)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(.linearGradient(Gradient(colors: [.CROrange, .CRPink]), startPoint: .top, endPoint: .bottom))
}
