import FirebaseAppCheck
import FirebaseCore
import Foundation

final class PoulePartyAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        #if DEBUG
        return AppCheckDebugProvider(app: app)
        #else
        // App Attest needs a real device: a release build in the simulator gets no token and callables reject it.
        return AppAttestProvider(app: app)
        #endif
    }
}
