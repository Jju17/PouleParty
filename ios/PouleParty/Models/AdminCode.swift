//
//  AdminCode.swift
//  PouleParty
//
//  Hardcoded code that gates the admin-mode wizard (lifts the maxPlayers
//  cap from 5 to 500). Obfuscation only — see PP-9 / PP-45: the worst
//  scenario if a user guesses the code is creating a >5-player party,
//  which has no business consequence.
//

enum AdminCode {
    static let value = "jujurahier"
}
