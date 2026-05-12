//
//  CreatePartyURLTests.swift
//  PoulePartyTests
//

import Foundation
import Testing
@testable import PouleParty

@MainActor
struct CreatePartyURLTests {

    @Test func frenchLocaleResolvesToCreerUnePartie() {
        let url = CreatePartyURL.url(for: "fr")
        #expect(url?.absoluteString == "https://pouleparty.be/creer-une-partie")
    }

    @Test func dutchLocaleResolvesToHetFeestjeOrganiseren() {
        let url = CreatePartyURL.url(for: "nl")
        #expect(url?.absoluteString == "https://pouleparty.be/een-feestje-organiseren")
    }

    @Test func englishLocaleResolvesToCreateAParty() {
        let url = CreatePartyURL.url(for: "en")
        #expect(url?.absoluteString == "https://pouleparty.be/create-a-party")
    }

    @Test func unknownLocaleFallsBackToEnglish() {
        let url = CreatePartyURL.url(for: "de")
        #expect(url?.absoluteString == "https://pouleparty.be/create-a-party")
    }

    @Test func nilLocaleFallsBackToEnglish() {
        let url = CreatePartyURL.url(for: nil)
        #expect(url?.absoluteString == "https://pouleparty.be/create-a-party")
    }

    @Test func mixedCaseLocaleFallsBackToEnglish() {
        // We compare against the lowercase BCP 47 codes Apple returns.
        // Anything else hits the fallback rather than crashing.
        let url = CreatePartyURL.url(for: "FR")
        #expect(url?.absoluteString == "https://pouleparty.be/create-a-party")
    }
}
