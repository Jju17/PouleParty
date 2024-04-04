//
//  Drink.swift
//  ChickenRush
//
//  Created by Julien Rahier on 23/03/2024.
//

import SwiftUI

struct Drink: Hashable {
    let name: String
    let symbolName: String
}

extension Drink {
    var symbol: Image {
        .getSymbol(symbolName)
    }
}

extension Drink {
    static var mock: Drink {
        return .init(name: "Wine", symbolName: "wineglass.fill")
    }

    static var mockList: [Drink] {
        return [
            .init(name: "Wine", symbolName: "wineglass.fill"),
            .init(name: "Water", symbolName: "waterbottle.fill"),
            .init(name: "Beer", symbolName: "beer.fill"),
            .init(name: "Juice", symbolName: "juice.fill"),
            .init(name: "Cocktail", symbolName: "cocktail.fill"),
            .init(name: "Shot", symbolName: "shot.glass.fill"),
            .init(name: "Wine", symbolName: "wineglass.fill"),
            .init(name: "Water", symbolName: "waterbottle.fill"),
            .init(name: "Beer", symbolName: "beer.fill"),
            .init(name: "Juice", symbolName: "juice.fill"),
            .init(name: "Cocktail", symbolName: "cocktail.fill"),
            .init(name: "Shot", symbolName: "shot.glass.fill")
        ]
    }
}
