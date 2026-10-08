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
        // so `AppAttestProvider` returns nil tokens. That's tolerable, the
        // server is in monitoring-only mode until enforce is flipped.
        return AppAttestProvider(app: app)
        #endif
    }
}
