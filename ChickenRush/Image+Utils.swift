//
//  Image+Utils.swift
//  ChickenRush
//
//  Created by Julien Rahier on 23/03/2024.
//

import SwiftUI

extension Image {
    static func getSymbol(_ symbolName: String, fallbackSymbolName: String = "questionmark") -> Image {

        // Check if symbol exists from Apple SF Symbol library
        if UIImage(systemName: symbolName) != nil {
            return Image(systemName: symbolName)
        }

        // Check if symbol exists from assets
        if UIImage(named: symbolName) != nil {
            return Image(symbolName)
        }

        return Image(systemName: fallbackSymbolName)
    }
}
