//
//  CreatePartyURL.swift
//  PouleParty
//
//  Builds the localized "create a party" landing-page URL so the Home
//  CTA can route the user to the right page (PP-46). The URL slugs are
//  the same ones the web routes are registered under.
//

import Foundation

enum CreatePartyURL {
    /// Map a BCP 47 language code (`Locale.current.language.languageCode`)
    /// to the matching path segment. Anything outside fr / nl falls back
    /// to the English route. Returns nil only if `URL(string:)` rejects
    /// the literal — the slugs are ASCII so callers should treat nil as
    /// "shouldn't happen".
    static func url(for languageCode: String?) -> URL? {
        let path: String
        switch languageCode {
        case "fr": path = "creer-une-partie"
        case "nl": path = "een-feestje-organiseren"
        default: path = "create-a-party"
        }
        return URL(string: "https://pouleparty.be/\(path)")
    }
}
