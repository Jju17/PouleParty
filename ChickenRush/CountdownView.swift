//
//  CountdownView.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import SwiftUI

struct CountdownView: View {
    @State var nowDate: Date = .now
    @Binding var nextRadiusUpdate: Date

    private var timer: Timer {
        Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            if nowDate < nextRadiusUpdate {
                self.nowDate = Date()
            }
        }
    }

    private var countDownComponents: DateComponents {
        return Date.countdownDateComponents(from: self.nowDate, to: self.nextRadiusUpdate)
    }

    var body: some View {
        Text("Map update in: \(self.countDownComponents.formattedMinutes):\(self.countDownComponents.formattedSeconds)")
            .onAppear {
                let _ = self.timer
            }
    }
}

#Preview {
    CountdownView(nextRadiusUpdate: .constant(Date.now.addingTimeInterval(100)))
}
