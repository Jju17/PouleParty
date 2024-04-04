//
//  DrinksListView.swift
//  ChickenRush
//
//  Created by Julien Rahier on 23/03/2024.
//

import SwiftUI

struct DrinksListView: View {
    @Binding var isAnimActive: Bool 
    @State private var offSet: CGFloat = .zero
    @State private var timer: Timer?
    @State private var width: CGFloat = .zero
    @State private var interval: TimeInterval = 0.05
    var drinks: [Drink]

    private func setTimer(withTimeInterval: TimeInterval) {
        self.timer?.invalidate()
        self.timer = Timer.scheduledTimer(withTimeInterval: self.interval, repeats: true) { _ in
            withAnimation {
                self.offSet -= 120
            }
            if self.offSet < -700 {
                self.offSet = (self.width / 2) - 290 // (-240 -50)
            }
            if self.interval > Double.random(in: 0.2...0.5) {
                self.timer?.invalidate()
                self.isAnimActive = false
            } else {
                self.interval += 0.01
                self.setTimer(withTimeInterval: self.interval)
            }
        }
    }

    var body: some View {
        GeometryReader { proxy in
            HStack(alignment: .center, spacing: 20) {
                ForEach(drinks, id: \.self) { drink in
                    DrinkView(drink: drink)
                }
            }
            .offset(x: offSet)
            .onAppear {
                self.width = proxy.size.width
                self.offSet = (self.width / 2) - 50
            }
        }
        .frame(height: 100)
        .onAppear {
            if self.isAnimActive {
                self.setTimer(withTimeInterval: self.interval)
            }
        }
        .onChange(of: isAnimActive, initial: false) { oldValue, newValue in
            if newValue {
                self.setTimer(withTimeInterval: 0.05)
            } else {
                self.timer?.invalidate()
                self.interval = 0.05
            }
        }
    }
}

#Preview {
    DrinksListView(isAnimActive: .constant(false), drinks: Drink.mockList)
        .background(.gray)
}
