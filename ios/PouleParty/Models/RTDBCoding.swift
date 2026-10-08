//
//  RTDBCoding.swift
//  PouleParty
//

import Foundation

func rtdbDouble(_ value: Any?) -> Double? {
    switch value {
    case let d as Double: return d
    case let i as Int: return Double(i)
    case let n as NSNumber: return n.doubleValue
    default: return nil
    }
}
