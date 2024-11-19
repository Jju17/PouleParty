//
//  CustomAlertView.swift
//  ChickenRush
//
//  Created by Julien Rahier on 11/18/24.
//

import SwiftUI

struct CustomAlertView: View {
    @Binding var text: String
    var onConfirm: () -> Void
    var onCancel: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Text("Please enter your password")
                .font(.headline)

            TextField("Password", text: $text)
                .textFieldStyle(RoundedBorderTextFieldStyle())

            HStack {
                Button("Cancel") {
                    onCancel()
                }
                .padding()
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)

                Button("Confirm") {
                    onConfirm()
                }
                .padding()
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(8)
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(12)
        .shadow(radius: 10)
    }
}
