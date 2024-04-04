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
        ZStack(alignment: .center) {
            RoundedRectangle(cornerRadius: 15)
                .fill(Color.CKRYellow)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .shadow(color: .gray, radius: 2, x: 3, y: 3)
            Button {
                self.action()
            } label: {
                Text(self.titleKey)
                    .fontWeight(.bold)
                    .foregroundStyle(.black)
            }
        }
        .frame(maxHeight: 100)
    }
}

#Preview {
    ZStack {
        SelectionButton("Sign up") {

        }
        .frame(width: 300, height: 90)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background { Color.CRPink.ignoresSafeArea() }
}
