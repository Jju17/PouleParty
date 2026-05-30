//
//  RTDBCoding.swift
//  PouleParty
//

import Foundation

/// Coerces a Realtime Database numeric leaf into a `Double`. RTDB hands numbers
/// back as `Double`, `Int`, or `NSNumber` depending on the value, so position
/// decoding (PP-102) normalizes through this helper.
func rtdbDouble(_ value: Any?) -> Double? {
    switch value {
    case let d as Double: return d
    case let i as Int: return Double(i)
    case let n as NSNumber: return n.doubleValue
    default: return nil
    }
}
