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

/// Text view using Bangers font with kern on the last character to prevent glyph clipping.
struct BangerText: View {
    private let attributedString: AttributedString

    init(_ text: String, size: CGFloat) {
        var attr = AttributedString(text)
        attr.font = .custom("Bangers-Regular", fixedSize: size)
        if let lastIndex = attr.characters.indices.last {
            attr[lastIndex..<attr.endIndex].kern = size * 0.25
        }
        self.attributedString = attr
    }

    var body: some View {
        Text(attributedString)
    }
}
