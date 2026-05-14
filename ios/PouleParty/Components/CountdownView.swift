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
    /// PP-17 — when the game's `endDate` is reached the bar flips to
    /// the `.ended` phase: red "Overtime:" label + `+MM:SS` delta.
    /// Optional so legacy callsites (e.g. previews) keep working.
    var endDate: Date? = nil
    var isChicken: Bool = false

    private enum Phase {
        case preChickenStart
        case headStart
        case inGame
        /// PP-17 — game timer has run out; show overtime delta.
        case ended
    }

    private var phase: Phase {
        if let end = endDate, nowDate >= end {
            return .ended
        }
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
        case .ended:
            return endDate ?? .now
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
        case .ended:
            return "Overtime:"
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
        switch phase {
        case .ended:
            // PP-17: fixed red, no pulsation — phase can last
            // arbitrarily long. Crossfade between phases is driven by
            // the parent's `.animation` modifier.
            Text("\(label) \(formatOvertime(now: nowDate, endDate: endDate ?? nowDate))")
                .foregroundStyle(Color.hunterRed)
                .transition(.opacity)
        default:
            Text("\(label) \(String(format: "%02d", minutesRemaining)):\(String(format: "%02d", secondsRemaining))")
                .transition(.opacity)
        }
    }
}

#Preview {
    CountdownView(nowDate: .constant(.now), nextUpdateDate: .constant(.now.addingTimeInterval(60)))
}
