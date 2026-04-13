import Testing
import Foundation
@testable import PouleParty

struct MigrationManagerTests {

    // MARK: - Version comparison (the core logic used by MigrationManager)

    @Test func numericComparisonOrdersCorrectly() {
        #expect("1.3.0".compare("1.4.0", options: .numeric) == .orderedAscending)
        #expect("1.4.0".compare("1.4.0", options: .numeric) == .orderedSame)
        #expect("1.5.0".compare("1.4.0", options: .numeric) == .orderedDescending)
        #expect("0.9.9".compare("1.0.0", options: .numeric) == .orderedAscending)
        #expect("2.0.0".compare("1.4.0", options: .numeric) == .orderedDescending)
        #expect("1.4.0".compare("1.4.1", options: .numeric) == .orderedAscending)
        #expect("10.0.0".compare("9.99.99", options: .numeric) == .orderedDescending)
    }

    @Test func emptyVersionIsLessThanAny() {
        #expect("".compare("1.4.0", options: .numeric) == .orderedAscending)
    }

    @Test func defaultNilVersionTreatedAsZero() {
        #expect("0.0.0".compare("1.4.0", options: .numeric) == .orderedAscending)
    }

    @Test func patchVersionComparison() {
        #expect("1.3.9".compare("1.4.0", options: .numeric) == .orderedAscending)
        #expect("1.4.0".compare("1.3.9", options: .numeric) == .orderedDescending)
    }

    @Test func majorVersionJump() {
        #expect("1.99.99".compare("2.0.0", options: .numeric) == .orderedAscending)
    }

    @Test func sameVersionDifferentPatch() {
        #expect("1.4.0".compare("1.4.0", options: .numeric) == .orderedSame)
        #expect("1.4.1".compare("1.4.0", options: .numeric) == .orderedDescending)
    }

    // MARK: - runIfNeeded stamps the version

    @Test func runIfNeededSetsVersionStamp() {
        let defaults = UserDefaults.standard
        let key = AppConstants.prefLastMigratedVersion
        MigrationManager.runIfNeeded()
        let stored = defaults.string(forKey: key)
        #expect(stored != nil)
        #expect(stored != "0.0.0")
    }

    // MARK: - Edge case

    @Test func malformedVersionDoesNotCrash() {
        let defaults = UserDefaults.standard
        let key = AppConstants.prefLastMigratedVersion
        defaults.set("abc", forKey: key)
        MigrationManager.runIfNeeded()
        let stored = defaults.string(forKey: key)
        #expect(stored != "abc")
    }
}
