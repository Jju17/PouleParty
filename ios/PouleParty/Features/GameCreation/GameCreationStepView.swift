//
//  GameCreationStepView.swift
//  PouleParty
//
//  Protocol every step view conforms to, so all steps share the same
//  construction shape and identity.
//

import ComposableArchitecture
import SwiftUI

protocol GameCreationStepView: View {
    /// The step identifier this view renders.
    static var step: GameCreationStep { get }

    /// The reducer store. Every step reads/writes through `store`.
    var store: StoreOf<GameCreationFeature> { get }

    init(store: StoreOf<GameCreationFeature>)
}
