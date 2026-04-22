//
//  StripeClient.swift
//  PouleParty
//
//  Thin wrapper around the Stripe-adjacent Cloud Functions. Never touches
//  Stripe secret keys directly — everything sensitive lives server-side.
//

import ComposableArchitecture
import FirebaseFirestore
import FirebaseFunctions
import Foundation

struct StripeClient {
    /// Forfait creator flow. Returns the pre-created `gameId` plus all fields
    /// the iOS PaymentSheet needs. The game doc is in `.pendingPayment` until
    /// the webhook flips it to `.waiting` on `payment_intent.succeeded`.
    var createCreatorPaymentSheet: (CreatorPaymentRequest) async throws -> CreatorPaymentSheetParams

    /// Caution hunter deposit flow. Called at registration time. Webhook
    /// writes `registrations/{uid}` on success.
    var createHunterPaymentSheet: (String) async throws -> HunterPaymentSheetParams

    /// Validates a Stripe promotion code (Forfait only). Server re-validates
    /// at redemption time — this is informational.
    var validatePromoCode: (String) async throws -> PromoCodeValidation

    /// Used when the validated promo code gives 100% off. Skips PaymentSheet
    /// entirely — server creates the game doc directly.
    var redeemFreeCreation: (CreatorPaymentRequest) async throws -> String

    struct CreatorPaymentRequest: Equatable {
        let gameConfig: Game
        let promoCodeId: String?
    }

    struct CreatorPaymentSheetParams: Equatable {
        let gameId: String
        let paymentIntentClientSecret: String
        let ephemeralKeySecret: String
        let customerId: String
        let amountCents: Int
    }

    struct HunterPaymentSheetParams: Equatable {
        let paymentIntentClientSecret: String
        let ephemeralKeySecret: String
        let customerId: String
        let amountCents: Int
    }

    struct PromoCodeValidation: Equatable {
        let valid: Bool
        let promoCodeId: String?
        let percentOff: Double?
        let amountOffCents: Int?
        let freeOverride: Bool
    }
}

extension StripeClient: TestDependencyKey {
    static let testValue = StripeClient(
        createCreatorPaymentSheet: { _ in .init(gameId: "", paymentIntentClientSecret: "", ephemeralKeySecret: "", customerId: "", amountCents: 0) },
        createHunterPaymentSheet: { _ in .init(paymentIntentClientSecret: "", ephemeralKeySecret: "", customerId: "", amountCents: 0) },
        validatePromoCode: { _ in .init(valid: false, promoCodeId: nil, percentOff: nil, amountOffCents: nil, freeOverride: false) },
        redeemFreeCreation: { _ in "" }
    )
}

extension DependencyValues {
    var stripeClient: StripeClient {
        get { self[StripeClient.self] }
        set { self[StripeClient.self] = newValue }
    }
}

extension StripeClient: DependencyKey {
    static var liveValue: StripeClient {
        let functions = Functions.functions(region: "europe-west1")

        return StripeClient(
            createCreatorPaymentSheet: { request in
                let payload: [String: Any] = [
                    "gameConfig": try encodeGameConfig(request.gameConfig),
                    "promoCodeId": request.promoCodeId as Any,
                ].compactMapValues { $0 is NSNull ? nil : $0 }

                let result = try await functions.httpsCallable("createCreatorPaymentSheet").call(payload)
                guard let dict = result.data as? [String: Any],
                      let gameId = dict["gameId"] as? String,
                      let clientSecret = dict["paymentIntentClientSecret"] as? String,
                      let ephKey = dict["ephemeralKeySecret"] as? String,
                      let customerId = dict["customerId"] as? String,
                      let amount = dict["amountCents"] as? Int
                else { throw StripeClientError.malformedResponse }
                return .init(
                    gameId: gameId,
                    paymentIntentClientSecret: clientSecret,
                    ephemeralKeySecret: ephKey,
                    customerId: customerId,
                    amountCents: amount
                )
            },
            createHunterPaymentSheet: { gameId in
                let result = try await functions.httpsCallable("createHunterPaymentSheet").call(["gameId": gameId])
                guard let dict = result.data as? [String: Any],
                      let clientSecret = dict["paymentIntentClientSecret"] as? String,
                      let ephKey = dict["ephemeralKeySecret"] as? String,
                      let customerId = dict["customerId"] as? String,
                      let amount = dict["amountCents"] as? Int
                else { throw StripeClientError.malformedResponse }
                return .init(
                    paymentIntentClientSecret: clientSecret,
                    ephemeralKeySecret: ephKey,
                    customerId: customerId,
                    amountCents: amount
                )
            },
            validatePromoCode: { code in
                let result = try await functions.httpsCallable("validatePromoCode").call(["code": code])
                guard let dict = result.data as? [String: Any],
                      let valid = dict["valid"] as? Bool
                else { throw StripeClientError.malformedResponse }
                if !valid {
                    return .init(valid: false, promoCodeId: nil, percentOff: nil, amountOffCents: nil, freeOverride: false)
                }
                return .init(
                    valid: true,
                    promoCodeId: dict["promoCodeId"] as? String,
                    percentOff: dict["percentOff"] as? Double,
                    amountOffCents: dict["amountOff"] as? Int,
                    freeOverride: dict["freeOverride"] as? Bool ?? false
                )
            },
            redeemFreeCreation: { request in
                guard let promoCodeId = request.promoCodeId else { throw StripeClientError.missingPromoCode }
                let payload: [String: Any] = [
                    "gameConfig": try encodeGameConfig(request.gameConfig),
                    "promoCodeId": promoCodeId,
                ]
                let result = try await functions.httpsCallable("redeemFreeCreation").call(payload)
                guard let dict = result.data as? [String: Any],
                      let gameId = dict["gameId"] as? String
                else { throw StripeClientError.malformedResponse }
                return gameId
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Encoding / errors
// ---------------------------------------------------------------------------

enum StripeClientError: LocalizedError {
    case malformedResponse
    case missingPromoCode

    var errorDescription: String? {
        switch self {
        case .malformedResponse: return "Unexpected response from payment service."
        case .missingPromoCode: return "Promo code is required."
        }
    }
}

/// Flattens a `Game` to the shape expected by `createCreatorPaymentSheet` /
/// `redeemFreeCreation` (mirrors the TypeScript `PendingGamePayload`). We
/// explicitly materialise millis timestamps because the Firebase Functions
/// SDK serialises `Timestamp` as `{_seconds, _nanoseconds}` which the server
/// doesn't parse.
private func encodeGameConfig(_ game: Game) throws -> [String: Any] {
    [
        "name": game.name,
        "maxPlayers": game.maxPlayers,
        "gameMode": game.gameMode.rawValue,
        "chickenCanSeeHunters": game.chickenCanSeeHunters,
        "foundCode": game.foundCode,
        "timing": [
            "startMillis": Int(game.timing.start.dateValue().timeIntervalSince1970 * 1000),
            "endMillis": Int(game.timing.end.dateValue().timeIntervalSince1970 * 1000),
            "headStartMinutes": game.timing.headStartMinutes,
        ],
        "zone": [
            "center": ["latitude": game.zone.center.latitude, "longitude": game.zone.center.longitude],
            "finalCenter": game.zone.finalCenter.map { ["latitude": $0.latitude, "longitude": $0.longitude] }
                ?? ["latitude": game.zone.center.latitude, "longitude": game.zone.center.longitude],
            "radius": game.zone.radius,
            "shrinkIntervalMinutes": game.zone.shrinkIntervalMinutes,
            "shrinkMetersPerUpdate": game.zone.shrinkMetersPerUpdate,
            "driftSeed": game.zone.driftSeed,
        ],
        "pricing": [
            "model": game.pricing.model.rawValue,
            "pricePerPlayer": game.pricing.pricePerPlayer,
            "deposit": game.pricing.deposit,
            "commission": game.pricing.commission,
        ],
        "registration": [
            "required": game.registration.required,
            "closesMinutesBefore": (game.registration.closesMinutesBefore.map { $0 as Any }) ?? NSNull(),
        ],
        "powerUps": [
            "enabled": game.powerUps.enabled,
            "enabledTypes": game.powerUps.enabledTypes,
        ],
    ]
}
