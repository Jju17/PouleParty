//
//  Date+Utils.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import Foundation

extension Date {

    /// Create a date from specified parameters
    ///
    /// - Parameters:
    ///   - year: The desired year
    ///   - month: The desired month
    ///   - day: The desired day
    ///   - hour: The desired hour
    /// - Returns: A `Date` object
    static func from(year: Int, month: Int, day: Int, hour: Int? = nil) -> Date {
        let calendar = Calendar(identifier: .gregorian)
        var dateComponents = DateComponents()
        dateComponents.year = year
        dateComponents.month = month
        dateComponents.day = day
        if let hour {
            dateComponents.hour = hour
        }
        return calendar.date(from: dateComponents) ?? Date()
    }

    static func countdownDateComponents(from fromDate: Date, to toDate: Date) -> DateComponents {
        return Calendar.current.dateComponents([.day, .hour, .minute, .second], from: fromDate, to: toDate)
    }

    static func countDownString(from fromDate: Date, to toDate: Date) -> String {
        let components = Date.countdownDateComponents(from: fromDate, to: toDate)

        return String(format: "%02d:%02d:%02d:%02d",
                              components.day ?? 00,
                              components.hour ?? 00,
                              components.minute ?? 00,
                              components.second ?? 00)
    }
}
