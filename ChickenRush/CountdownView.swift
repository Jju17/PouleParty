//
//  CountdownView.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import SwiftUI

struct CountdownView: View {
    @Binding var nowDate: Date
    @Binding var nextUpdateDate: Date?

    var minutesRemaining: Int {
        let dateCompponent = self.nowDate.countdownDateComponents(to: nextUpdateDate ?? .now)
        return dateCompponent.minute ?? 00
    }

    var secondsRemaining: Int {
        let dateCompponent = self.nowDate.countdownDateComponents(to: nextUpdateDate ?? .now)
        return dateCompponent.second ?? 00
    }

    var body: some View {
        Text("Map update in: \(self.minutesRemaining):\(self.secondsRemaining)")
    }
}

#Preview {
    CountdownView(nowDate: .constant(.now), nextUpdateDate: .constant(.now.addingTimeInterval(60)))
}
