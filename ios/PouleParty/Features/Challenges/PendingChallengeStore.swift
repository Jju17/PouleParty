import Foundation

enum PendingChallengeStore {

    private static let key = AppConstants.prefPendingChallenges
    nonisolated(unsafe) private static var defaults: UserDefaults = .standard

    static func inject(_ store: UserDefaults) { defaults = store }
    static func resetForTesting() { defaults = .standard }

    static func ids(forGame gameId: String) -> Set<String> {
        Set(rawDict()[gameId] ?? [])
    }

    static func add(_ challengeId: String, forGame gameId: String) {
        var dict = rawDict()
        var ids = Set(dict[gameId] ?? [])
        ids.insert(challengeId)
        dict[gameId] = Array(ids).sorted()
        write(dict)
    }

    static func remove(_ challengeId: String, forGame gameId: String) {
        var dict = rawDict()
        var ids = Set(dict[gameId] ?? [])
        ids.remove(challengeId)
        if ids.isEmpty {
            dict[gameId] = nil
        } else {
            dict[gameId] = Array(ids).sorted()
        }
        write(dict)
    }

    static func clear(gameId: String) {
        var dict = rawDict()
        dict[gameId] = nil
        write(dict)
    }

    private static func rawDict() -> [String: [String]] {
        guard let any = defaults.dictionary(forKey: key) else { return [:] }
        var out: [String: [String]] = [:]
        for (gameId, value) in any {
            if let ids = value as? [String] {
                out[gameId] = ids
            }
        }
        return out
    }

    private static func write(_ dict: [String: [String]]) {
        if dict.isEmpty {
            defaults.removeObject(forKey: key)
        } else {
            defaults.set(dict, forKey: key)
        }
    }
}
