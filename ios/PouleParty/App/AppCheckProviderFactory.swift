//
//  AppCheckProviderFactory.swift
//  PouleParty
//
//  CRIT-4 (audit 2026-05-17): App Check protects `createPendingRegistration`
//  (Stripe payment endpoint) against bot spam. Without enforcement on the
//  Function side, the SDK still ships tokens that Firebase tracks on the
//  App Check monitoring dashboard — useful to verify token coverage before
//  flipping `enforceAppCheck: true` on the server.
//
//  - DEBUG builds + Simulator: `AppCheckDebugProvider`. Generates a per-device
//    token at first launch, logged to Xcode console. The token must be
//    registered in Firebase Console > App Check > iOS app > Manage debug
//    tokens.
//  - Release builds on real devices: `AppAttestProvider`. Backed by the
//    Apple `DeviceCheck.appattestKey` API, which signs an attestation from
//    Apple's CA proving the binary is genuine and the device is a real iOS
//    device with a valid Apple ID.
//

import FirebaseAppCheck
import FirebaseCore
import Foundation

final class PoulePartyAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        #if DEBUG
        return AppCheckDebugProvider(app: app)
        #else
        // App Attest only works on real iOS devices (iOS 14+). On a release
        // build running in the simulator (rare but happens during TestFlight
        // sideload tests) Apple's DeviceCheck refuses to issue an attestation,
        // so `AppAttestProvider` returns nil tokens. That's tolerable — the
        // server is in monitoring-only mode until enforce is flipped.
        return AppAttestProvider(app: app)
        #endif
    }
}
