//
//  LiveActivityClient.swift
//  PouleParty
//

import ActivityKit
import ComposableArchitecture
import os

private let logger = Logger(subsystem: "dev.rahier.pouleparty", category: "LiveActivity")

struct LiveActivityClient {
    var start: (PoulePartyAttributes, PoulePartyAttributes.ContentState) -> Void
    var update: (PoulePartyAttributes.ContentState) -> Void
    var end: (PoulePartyAttributes.ContentState?) -> Void
}

extension LiveActivityClient: TestDependencyKey {
    static let testValue = LiveActivityClient(
        start: { _, _ in },
        update: { _ in },
        end: { _ in }
    )
}

extension LiveActivityClient: DependencyKey {
    static let liveValue = LiveActivityClient(
        start: { attributes, initialState in
            guard ActivityAuthorizationInfo().areActivitiesEnabled else {
                logger.info("Live Activities are not enabled")
                return
            }
            // End any existing activities first
            for activity in Activity<PoulePartyAttributes>.activities {
                Task {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
            do {
                let activity = try Activity<PoulePartyAttributes>.request(
                    attributes: attributes,
                    content: .init(state: initialState, staleDate: nil),
                    pushType: nil
                )
                logger.info("Started Live Activity: \(activity.id)")
            } catch {
                logger.error("Failed to start Live Activity: \(error)")
            }
        },
        update: { state in
            Task {
                for activity in Activity<PoulePartyAttributes>.activities {
                    await activity.update(.init(state: state, staleDate: nil))
                }
            }
        },
        end: { finalState in
            Task {
                for activity in Activity<PoulePartyAttributes>.activities {
                    await activity.end(
                        finalState.map { .init(state: $0, staleDate: nil) },
                        dismissalPolicy: .default
                    )
                }
            }
        }
    )
}

extension DependencyValues {
    var liveActivityClient: LiveActivityClient {
        get { self[LiveActivityClient.self] }
        set { self[LiveActivityClient.self] = newValue }
    }
}
