import Foundation
import Testing
@testable import PouleParty

struct GameCreationFormattersTests {
    @Test func durationsUseTheLocaleUnits() {
        let english = GameCreationFormatters.duration(90, locale: Locale(identifier: "en_GB"))
        #expect(english.contains("1") && english.contains("30"))
        #expect(GameCreationFormatters.duration(60, locale: Locale(identifier: "en_GB")).contains("1"))
        #expect(!GameCreationFormatters.duration(60, locale: Locale(identifier: "en_GB")).contains("0m"))
    }

    @Test func datesAreShownInBrusselsTime() {
        let summerNoonUTC = Date(timeIntervalSince1970: 1_780_747_200)
        let text = GameCreationFormatters.date(summerNoonUTC, locale: Locale(identifier: "fr_BE"))
        #expect(text.contains("14:00"))
    }
}
