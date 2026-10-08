@testable import PouleParty

extension Game {
    /// Sets the single chicken, preserving every existing hunter / GameMaster.
    /// Passing "" clears the chicken (no-chicken fixture).
    mutating func setChickenId(_ uid: String) {
        roles = roles.filter { $0.value != "chicken" }
        if !uid.isEmpty { roles[uid] = "chicken" }
    }

    /// Replaces the full hunter set, preserving the chicken / GameMasters.
    mutating func setHunterIds(_ uids: [String]) {
        roles = roles.filter { $0.value != "hunter" }
        for uid in uids where !uid.isEmpty { roles[uid] = "hunter" }
    }

    /// Replaces the full GameMaster set, preserving the chicken / hunters.
    mutating func setGameMasterIds(_ uids: [String]) {
        roles = roles.filter { $0.value != "gameMaster" }
        for uid in uids where !uid.isEmpty { roles[uid] = "gameMaster" }
    }
}
