//
//  ProfanityFilterTests.swift
//  PoulePartyTests
//

import Testing
@testable import PouleParty

struct ProfanityFilterTests {

    @Test func detectsBlockedEnglishWords() {
        #expect(ProfanityFilter.containsProfanity("hello fuck you") == true)
        #expect(ProfanityFilter.containsProfanity("shit") == true)
        #expect(ProfanityFilter.containsProfanity("asshole") == true)
    }

    @Test func detectsBlockedFrenchWords() {
        #expect(ProfanityFilter.containsProfanity("merde") == true)
        #expect(ProfanityFilter.containsProfanity("connard") == true)
        #expect(ProfanityFilter.containsProfanity("putain") == true)
    }

    @Test func allowsCleanText() {
        #expect(ProfanityFilter.containsProfanity("hello world") == false)
        #expect(ProfanityFilter.containsProfanity("PouleParty") == false)
        #expect(ProfanityFilter.containsProfanity("Julien") == false)
    }

    @Test func detectsLeetspeakSubstitutions() {
        #expect(ProfanityFilter.containsProfanity("sh1t") == true)   // 1->i
        #expect(ProfanityFilter.containsProfanity("@$$hole") == true) // @->a, $->s
        #expect(ProfanityFilter.containsProfanity("m3rde") == true)  // 3->e
    }

    @Test func handlesDiacriticsViaNormalization() {
        #expect(ProfanityFilter.containsProfanity("enculé") == true)
        #expect(ProfanityFilter.containsProfanity("pédé") == true)
        #expect(ProfanityFilter.containsProfanity("bâtard") == true)
    }

    @Test func isCaseInsensitive() {
        #expect(ProfanityFilter.containsProfanity("FUCK") == true)
        #expect(ProfanityFilter.containsProfanity("Merde") == true)
        #expect(ProfanityFilter.containsProfanity("CONNARD") == true)
    }

    @Test func substringMatchIsAggressive() {
        // "ass" is in the blocked list, so "class" will match (substring match)
        // This documents intended behavior — the filter is aggressive
        #expect(ProfanityFilter.containsProfanity("class") == true)
    }

    @Test func emptyStringReturnsFalse() {
        #expect(ProfanityFilter.containsProfanity("") == false)
    }

    @Test func singleBlockedWord() {
        #expect(ProfanityFilter.containsProfanity("fuck") == true)
    }

    @Test func multipleBlockedWords() {
        #expect(ProfanityFilter.containsProfanity("fuck shit merde") == true)
    }

    @Test func numbersOnlyReturnsFalse() {
        #expect(ProfanityFilter.containsProfanity("123456") == false)
    }

    @Test func emojisReturnFalse() {
        #expect(ProfanityFilter.containsProfanity("\u{1F414}\u{1F389}") == false)
    }
}
