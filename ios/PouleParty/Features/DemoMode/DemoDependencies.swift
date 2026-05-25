//
//  DemoDependencies.swift
//  PouleParty
//

import CoreLocation
import FirebaseFirestore
import Foundation

extension ApiClient {
    static var demo: ApiClient {
        ApiClient(
            findActiveGame: { _ in nil },
            submitFoundCode: { _, _, _ in },
            getFoundCode: { _ in MockDemoData.liveGame.foundCode },
            deleteConfig: { _ in },
            getConfig: { _ in MockDemoData.liveGame },
            findGameByCode: { _ in MockDemoData.liveGame },
            registerHunter: { _, _ in },
            updateGameStatus: { _, _ in },
            chickenLocationStream: { _ in
                demoStream { continuation in
                    continuation.yield(MockDemoData.chickenLocation)
                }
            },
            gameConfigStream: { gameId in
                demoStream { continuation in
                    let game = gameId == MockDemoData.doneGame.id ? MockDemoData.doneGame : MockDemoData.liveGame
                    continuation.yield(game)
                }
            },
            hunterLocationsStream: { _ in
                demoStream { continuation in
                    continuation.yield(MockDemoData.hunterLocations)
                }
            },
            setChickenLocation: { _, _, _ in },
            setConfig: { _ in },
            setHunterLocation: { _, _, _ in },
            collectPowerUp: { _, _, _ in },
            activatePowerUp: { _, _, _, _ in },
            powerUpsStream: { _ in
                demoStream { continuation in
                    continuation.yield(MockDemoData.powerUps)
                }
            },
            updateHeartbeat: { _ in },
            fetchMyGames: { _ in [] },
            findRegistration: { _, userId in
                MockDemoData.registrations.first { $0.userId == userId }
            },
            createRegistration: { _, _ in },
            fetchAllRegistrations: { _ in MockDemoData.registrations },
            registrationsStream: { _ in
                demoStream { continuation in
                    continuation.yield(MockDemoData.registrations)
                }
            },
            challengesStream: {
                demoStream { continuation in
                    continuation.yield([])
                }
            },
            challengeCompletionsStream: { _ in
                demoStream { continuation in
                    continuation.yield([])
                }
            },
            hunterSubmissionsStream: { _, _ in
                demoStream { continuation in
                    continuation.yield([])
                }
            },
            pendingSubmissionsStream: { _ in
                demoStream { continuation in
                    continuation.yield([])
                }
            },
            submitChallenge: { _, _, _, _, _ in ChallengeSubmission() },
            validateChallengeSubmission: { _, _, _ in },
            decrementTotalPoints: { _, _ in },
            fetchUserNicknames: { _ in [:] },
            reportPlayer: { _, _, _ in },
            newGameId: { MockDemoData.liveGame.id },
            setGameMasterPassword: { _, _ in },
            clearGameMasterPassword: { _ in },
            joinAsGameMaster: { _, _ in
                JoinAsGameMasterResult(success: true, attemptsRemaining: 5, lockedUntilMs: nil)
            },
            designateChicken: { _, _ in },
            computeZoneConfiguration: { _ in
                ComputeZoneConfigurationOutput(
                    initialRadius: MockDemoData.liveGame.zone.radius,
                    validatedFinal: nil,
                    driftSeed: MockDemoData.liveGame.zone.driftSeed,
                    finalZoneRadius: 50,
                    interiorMargin: 200,
                    shrinkIntervalMinutes: 5,
                    shrinkMetersPerUpdate: 50,
                    circles: []
                )
            }
        )
    }

    /// Yields the demo value once and stays alive forever. The map
    /// screens treat a `.finish()` as "stream closed" and stop
    /// updating; for the demo we want the canned snapshot to remain
    /// the live truth, so we never finish.
    private static func demoStream<T>(_ seed: (AsyncStream<T>.Continuation) -> Void) -> AsyncStream<T> {
        AsyncStream { continuation in
            seed(continuation)
        }
    }
}

extension LocationClient {
    static var demo: LocationClient {
        LocationClient(
            authorizationStatus: { .authorizedWhenInUse },
            lastLocation: { MockDemoData.zoneCenter },
            requestWhenInUse: { },
            requestAlways: { },
            startTracking: {
                AsyncStream { continuation in
                    continuation.yield(MockDemoData.zoneCenter)
                }
            },
            stopTracking: { }
        )
    }
}

extension UserClient {
    static var demo: UserClient {
        UserClient(
            currentUserId: { MockDemoData.chickenUid },
            deleteAccount: { },
            fcmToken: { nil },
            saveNickname: { _ in },
            signInAnonymously: {
                SignInResult(uid: MockDemoData.chickenUid, isNewUser: false)
            }
        )
    }
}
