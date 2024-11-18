//
//  DrinkView.swift
//  ChickenRush
//
//  Created by Julien Rahier on 22/03/2024.
//

import SwiftUI

struct DrinkView: View {
    var drink: Drink

    var body: some View {
        ZStack(alignment: .center) {

            RoundedRectangle(cornerRadius: 20)
                .fill(.white)
            drink.symbol
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 50, height: 50)
                .foregroundStyle(LinearGradient(colors: [.crOrange, .crPink], startPoint: .top, endPoint: .bottom))
        }
        .frame(width: 100, height: 100, alignment: .center)
    }
}

#Preview {
    DrinkView(drink: .mock)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.gray)
}
