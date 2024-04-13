//
//  SelectionButton.swift
//  ChickenRush
//
//  Created by Julien Rahier on 03/04/2024.
//

import SwiftUI

struct SelectionButton: View {
    var titleKey: LocalizedStringKey
    var color: Color
    var action: () -> Void

    init(_ titleKey: LocalizedStringKey, color: Color = .black, action: @escaping () -> Void) {
        self.titleKey = titleKey
        self.color = color
        self.action = action
    }

    var body: some View {
        Button(action: self.action) {
            Text(self.titleKey)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .font(.banger(size: 20))
                .foregroundStyle(.white)
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(.white, lineWidth: 2)
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
