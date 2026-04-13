//
//  MyGame.swift
//  PouleParty
//
//  A game shown in the "My Games" list, tagged with the user's role (creator or hunter).
//

import Foundation

struct MyGame: Equatable, Identifiable {
    let game: Game
    let role: GameRole

    var id: String { game.id }
}
