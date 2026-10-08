import SwiftUI

/// The pixel font is unreadable below its 8 pt grid.
private let minimumGameboySize: CGFloat = 8

extension Font {
    static func banger(size: CGFloat) -> Font {
        return Font.custom("Bangers-Regular", size: size, relativeTo: .title2)
    }
    static func gameboy(size: CGFloat) -> Font {
        return Font.custom("Early GameBoy", size: max(size, minimumGameboySize), relativeTo: .caption)
    }
}

/// Text view using Bangers font with kern on the last character to prevent glyph clipping.
struct BangerText: View {
    private let attributedString: AttributedString

    init(_ key: LocalizedStringResource, size: CGFloat) {
        self.init(verbatim: String(localized: key), size: size)
    }

    @_disfavoredOverload
    init(_ text: String, size: CGFloat) {
        self.init(verbatim: text, size: size)
    }

    private init(verbatim text: String, size: CGFloat) {
        var attr = AttributedString(text)
        attr.font = .custom("Bangers-Regular", size: size, relativeTo: .title2)
        if let lastIndex = attr.characters.indices.last {
            attr[lastIndex..<attr.endIndex].kern = size * 0.25
        }
        self.attributedString = attr
    }

    var body: some View {
        Text(attributedString)
    }
}
