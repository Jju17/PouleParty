//
//  CountdownView.swift
//  PouleParty
//
//  Created by Julien Rahier on 15/03/2024.
//

import SwiftUI

struct CountdownView: View {
    @Binding var nowDate: Date
    @Binding var nextUpdateDate: Date?
    var chickenStartDate: Date? = nil
    var hunterStartDate: Date? = nil
    var isChicken: Bool = false

    private enum Phase {
        case preChickenStart
        case headStart
        case inGame
    }

    private var phase: Phase {
        guard let chickenStart = chickenStartDate else { return .inGame }
        guard let hunterStart = hunterStartDate else { return .inGame }

        if nowDate < chickenStart {
            return .preChickenStart
        } else if nowDate < hunterStart {
            return .headStart
        } else {
            return .inGame
        }
    }

    private var target: Date {
        switch phase {
        case .preChickenStart:
            return chickenStartDate ?? .now
        case .headStart:
            return hunterStartDate ?? .now
        case .inGame:
            return nextUpdateDate ?? .now
        }
    }

    private var label: String {
        switch phase {
        case .preChickenStart:
            return isChicken ? "You start in:" : "🐔 starts in:"
        case .headStart:
            return isChicken ? "🔍 Hunt starts in:" : "Hunt starts in:"
        case .inGame:
            return "Map update in:"
        }
    }

    var minutesRemaining: Int {
        let totalSeconds = Int(max(0, ceil(target.timeIntervalSince(nowDate))))
        return totalSeconds / 60
    }

    var secondsRemaining: Int {
        let totalSeconds = Int(max(0, ceil(target.timeIntervalSince(nowDate))))
        return totalSeconds % 60
    }

    var body: some View {
        Text("\(label) \(String(format: "%02d", minutesRemaining)):\(String(format: "%02d", secondsRemaining))")
    }
}

#Preview {
    CountdownView(nowDate: .constant(.now), nextUpdateDate: .constant(.now.addingTimeInterval(60)))
}
