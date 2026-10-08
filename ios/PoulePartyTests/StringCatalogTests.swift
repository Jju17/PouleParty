import Foundation
import Testing

/// Every visible string ships in French and Dutch, and no copy uses an em dash.
struct StringCatalogTests {
    private struct Catalog: Decodable {
        struct Entry: Decodable {
            let localizations: [String: Localization]?
            let shouldTranslate: Bool?
        }
        struct Localization: Decodable {
            struct Unit: Decodable { let value: String }
            let stringUnit: Unit?
            let variations: Variations?
        }
        struct Variations: Decodable {
            let plural: [String: Localization]?
        }
        let strings: [String: Entry]
    }

    private func loadCatalog() throws -> Catalog {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../Localizable.xcstrings")
            .standardized
        return try JSONDecoder().decode(Catalog.self, from: Data(contentsOf: url))
    }

    private func values(_ localization: Catalog.Localization) -> [String] {
        if let unit = localization.stringUnit { return [unit.value] }
        return localization.variations?.plural?.values.flatMap(values) ?? []
    }

    @Test func everyKeyIsTranslatedInFrenchAndDutch() throws {
        let catalog = try loadCatalog()
        let missing = catalog.strings.compactMap { key, entry -> String? in
            guard entry.shouldTranslate != false else { return nil }
            let locales = entry.localizations ?? [:]
            let gaps = ["fr", "nl"].filter { locales[$0].map(values)?.isEmpty ?? true }
            return gaps.isEmpty ? nil : "\(key) [\(gaps.joined(separator: ","))]"
        }
        #expect(missing.isEmpty, "\(missing.sorted())")
    }

    @Test func noTranslationUsesAnEmDash() throws {
        let catalog = try loadCatalog()
        let offenders = catalog.strings.flatMap { key, entry -> [String] in
            let texts = [key] + (entry.localizations ?? [:]).values.flatMap(values)
            return texts.contains { $0.contains("\u{2014}") } ? [key] : []
        }
        #expect(offenders.isEmpty, "\(offenders.sorted())")
    }
}
