//
//  Font+Utils.swift
//  PouleParty
//
//  Created by Julien Rahier on 04/04/2024.
//

import SwiftUI

extension Font {
    static func banger(size: CGFloat) -> Font {
        return Font.custom("Bangers-Regular", fixedSize: size)
    }
    static func gameboy(size: CGFloat) -> Font {
        return Font.custom("Early GameBoy", fixedSize: size)
    }
}
