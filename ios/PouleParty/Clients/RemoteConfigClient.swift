//
//  RemoteConfigClient.swift
//  PouleParty
//

import ComposableArchitecture
import FirebaseRemoteConfig
import Foundation

/// Runtime-tunable game values backed by Firebase Remote Config.
///
/// Getters are synchronous and return the currently-activated value, falling
/// back to the compiled defaults (registered via `setDefaults`) until the
/// first `activate()` resolves. Remote Config only ever *overrides* the
/// compiled defaults, so the app behaves correctly offline or before the
/// first fetch.
struct RemoteConfigClient {
    var adminCode: () -> String
    /// QA-debug code. Unlike `adminCode`, an empty value is returned as-is
    /// (no fallback to the compiled default) so the code can be disabled
    /// remotely by clearing the Remote Config value.
    var qaDebugCode: () -> String
    var codeMaxWrongAttempts: () -> Int
    var codeCooldownSeconds: () -> TimeInterval
    var defaultInitialRadius: () -> Double
    /// Fetches the latest values and activates them. Call once at launch.
    var activate: () async -> Void
}

extension RemoteConfigClient {
    enum Key: String {
        case adminCode = "admin_code"
        case qaDebugCode = "qa_debug_code"
        case codeMaxWrongAttempts = "found_code_max_wrong_attempts"
        case codeCooldownSeconds = "found_code_cooldown_seconds"
        case defaultInitialRadius = "default_initial_radius_meters"
    }

    /// Compiled fallbacks. Single source of truth shared by the live defaults
    /// and the test stub so they can never drift.
    static let compiledDefaults: [String: NSObject] = [
        Key.adminCode.rawValue: AdminCode.value as NSString,
        Key.qaDebugCode.rawValue: DebugCode.value as NSString,
        Key.codeMaxWrongAttempts.rawValue: NSNumber(value: AppConstants.codeMaxWrongAttempts),
        Key.codeCooldownSeconds.rawValue: NSNumber(value: AppConstants.codeCooldownSeconds),
        Key.defaultInitialRadius.rawValue: NSNumber(value: AppConstants.defaultInitialRadiusMeters),
    ]
}

extension RemoteConfigClient: TestDependencyKey {
    static let testValue = RemoteConfigClient(
        adminCode: { AdminCode.value },
        qaDebugCode: { DebugCode.value },
        codeMaxWrongAttempts: { AppConstants.codeMaxWrongAttempts },
        codeCooldownSeconds: { AppConstants.codeCooldownSeconds },
        defaultInitialRadius: { AppConstants.defaultInitialRadiusMeters },
        activate: {}
    )
}

extension RemoteConfigClient: DependencyKey {
    static let liveValue: RemoteConfigClient = {
        let remoteConfig = RemoteConfig.remoteConfig()
        let settings = RemoteConfigSettings()
        settings.minimumFetchInterval = 3600
        remoteConfig.configSettings = settings
        remoteConfig.setDefaults(compiledDefaults)

        return RemoteConfigClient(
            adminCode: {
                let value = remoteConfig.configValue(forKey: Key.adminCode.rawValue).stringValue
                return value.isEmpty ? AdminCode.value : value
            },
            qaDebugCode: {
                // Returned raw — an empty Remote Config value disables
                // debug-game creation (the match check requires non-empty).
                remoteConfig.configValue(forKey: Key.qaDebugCode.rawValue).stringValue
            },
            codeMaxWrongAttempts: {
                remoteConfig.configValue(forKey: Key.codeMaxWrongAttempts.rawValue).numberValue.intValue
            },
            codeCooldownSeconds: {
                remoteConfig.configValue(forKey: Key.codeCooldownSeconds.rawValue).numberValue.doubleValue
            },
            defaultInitialRadius: {
                remoteConfig.configValue(forKey: Key.defaultInitialRadius.rawValue).numberValue.doubleValue
            },
            activate: {
                _ = try? await remoteConfig.fetchAndActivate()
            }
        )
    }()
}

extension DependencyValues {
    var remoteConfigClient: RemoteConfigClient {
        get { self[RemoteConfigClient.self] }
        set { self[RemoteConfigClient.self] = newValue }
    }
}
