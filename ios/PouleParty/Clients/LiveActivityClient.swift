//
//  LiveActivityClient.swift
//  PouleParty
//

import ActivityKit
import ComposableArchitecture
import os

private let logger = Logger(category: "LiveActivity")

struct LiveActivityClient {
    var start: @Sendable (PoulePartyAttributes, PoulePartyAttributes.ContentState) async -> Void
    var update: @Sendable (PoulePartyAttributes.ContentState) async -> Void
    var end: @Sendable (PoulePartyAttributes.ContentState?) async -> Void
    var cleanupOrphaned: @Sendable () async -> Void
}

extension LiveActivityClient: TestDependencyKey {
    static let testValue = LiveActivityClient(
        start: { _, _ in },
        update: { _ in },
        end: { _ in },
        cleanupOrphaned: { }
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
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            do {
                let activity = try Activity<PoulePartyAttributes>.request(
                    attributes: attributes,
                    content: .init(
                        state: initialState,
                        staleDate: .now.addingTimeInterval(15 * 60)
                    ),
                    pushType: nil
                )
                logger.info("Started Live Activity: \(activity.id)")
            } catch {
                logger.error("Failed to start Live Activity: \(error)")
            }
        },
        update: { state in
            for activity in Activity<PoulePartyAttributes>.activities {
                await activity.update(.init(
                    state: state,
                    staleDate: .now.addingTimeInterval(15 * 60)
                ))
            }
        },
        end: { finalState in
            for activity in Activity<PoulePartyAttributes>.activities {
                await activity.end(
                    finalState.map { .init(state: $0, staleDate: nil) },
                    dismissalPolicy: .after(.now.addingTimeInterval(5 * 60))
                )
            }
        },
        cleanupOrphaned: {
            let activities = Activity<PoulePartyAttributes>.activities
            guard !activities.isEmpty else { return }
            logger.info("Cleaning up \(activities.count) orphaned Live Activities")
            for activity in activities {
                await activity.end(nil, dismissalPolicy: .immediate)
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
