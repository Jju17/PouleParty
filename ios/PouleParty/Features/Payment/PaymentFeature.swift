//
//  PaymentFeature.swift
//  PouleParty
//
//  Drives the Stripe PaymentSheet for two flows:
//  - Creator Forfait: pays up-front to unlock paid game creation. Optional
//    promo code. 100% off → backend creates the game directly, no PaymentSheet.
//  - Hunter Caution: pays the creator-defined deposit at registration time.
//

import ComposableArchitecture
import StripePaymentSheet
import SwiftUI
import UIKit

@Reducer
struct PaymentFeature {

    enum Context: Equatable {
        case creatorForfait(gameConfig: Game)
        case hunterCaution(gameId: String)

        var showsPromoCode: Bool {
            if case .creatorForfait = self { return true }
            return false
        }
    }

    @ObservableState
    struct State: Equatable {
        let context: Context
        var promoCodeInput: String = ""
        var validatedPromoCodeId: String? = nil
        var validatedDiscountLabel: String? = nil
        var freeOverride: Bool = false
        var promoValidating: Bool = false
        var promoError: String? = nil
        var preparing: Bool = false
        var prepareError: String? = nil
        var paymentConfig: PaymentSheetConfig? = nil
        var isPresentingSheet: Bool = false
        /// Non-nil once the backend has accepted: creator gets the pre-created
        /// `gameId` that the UI can listen to for status transitions, hunter
        /// gets `""` (registration is created server-side by the webhook).
        var completedGameId: String? = nil
        var completionError: String? = nil
    }

    struct PaymentSheetConfig: Equatable {
        let clientSecret: String
        let ephemeralKeySecret: String
        let customerId: String
        let amountCents: Int
        /// Only present on creator flow — game doc is pre-created in `.pendingPayment`.
        let gameId: String?
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case applyPromoTapped
        case clearPromoTapped
        case promoValidated(Result<StripeClient.PromoCodeValidation, Error>)
        case payTapped
        case prepareResponse(Result<PaymentSheetConfig, Error>)
        case freeRedeemResponse(Result<String, Error>)
        case sheetCompleted(PaymentSheetResult)
        case dismissPaymentFailedAlert
        case delegate(Delegate)

        @CasePathable
        enum Delegate: Equatable {
            /// Successful creator flow returns the (pre-created) gameId so the
            /// parent can observe its status flip from `.pendingPayment` → `.waiting`.
            case creatorPaymentConfirmed(gameId: String)
            /// Successful hunter flow — registration is being created server-side.
            case hunterPaymentConfirmed
            case cancelled
        }
    }

    enum PaymentSheetResult: Equatable {
        case completed
        case canceled
        case failed(message: String)
    }

    @Dependency(\.stripeClient) var stripeClient

    var body: some Reducer<State, Action> {
        BindingReducer()
        Reduce { state, action in
            switch action {
            case .binding:
                return .none

            case .applyPromoTapped:
                guard case .creatorForfait = state.context else { return .none }
                let code = state.promoCodeInput.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !code.isEmpty else { return .none }
                state.promoValidating = true
                state.promoError = nil
                return .run { [stripeClient] send in
                    await send(.promoValidated(Result { try await stripeClient.validatePromoCode(code) }))
                }

            case let .promoValidated(.success(validation)):
                state.promoValidating = false
                guard validation.valid else {
                    state.promoError = "Code invalide ou expiré"
                    return .none
                }
                state.validatedPromoCodeId = validation.promoCodeId
                state.freeOverride = validation.freeOverride
                if let pct = validation.percentOff {
                    state.validatedDiscountLabel = "-\(Int(pct))%"
                } else if let off = validation.amountOffCents {
                    state.validatedDiscountLabel = "-\(off / 100)€"
                } else {
                    state.validatedDiscountLabel = "Appliqué"
                }
                return .none

            case let .promoValidated(.failure(error)):
                state.promoValidating = false
                state.promoError = error.localizedDescription
                return .none

            case .clearPromoTapped:
                state.promoCodeInput = ""
                state.validatedPromoCodeId = nil
                state.validatedDiscountLabel = nil
                state.freeOverride = false
                state.promoError = nil
                return .none

            case .payTapped:
                state.preparing = true
                state.prepareError = nil
                switch state.context {
                case let .creatorForfait(gameConfig):
                    let promoCodeId = state.validatedPromoCodeId
                    if state.freeOverride, let promoCodeId {
                        return .run { [stripeClient] send in
                            await send(.freeRedeemResponse(Result {
                                try await stripeClient.redeemFreeCreation(
                                    .init(gameConfig: gameConfig, promoCodeId: promoCodeId)
                                )
                            }))
                        }
                    }
                    return .run { [stripeClient] send in
                        await send(.prepareResponse(Result {
                            let params = try await stripeClient.createCreatorPaymentSheet(
                                .init(gameConfig: gameConfig, promoCodeId: promoCodeId)
                            )
                            return PaymentSheetConfig(
                                clientSecret: params.paymentIntentClientSecret,
                                ephemeralKeySecret: params.ephemeralKeySecret,
                                customerId: params.customerId,
                                amountCents: params.amountCents,
                                gameId: params.gameId
                            )
                        }))
                    }
                case let .hunterCaution(gameId):
                    return .run { [stripeClient] send in
                        await send(.prepareResponse(Result {
                            let params = try await stripeClient.createHunterPaymentSheet(gameId)
                            return PaymentSheetConfig(
                                clientSecret: params.paymentIntentClientSecret,
                                ephemeralKeySecret: params.ephemeralKeySecret,
                                customerId: params.customerId,
                                amountCents: params.amountCents,
                                gameId: nil
                            )
                        }))
                    }
                }

            case let .prepareResponse(.success(config)):
                state.preparing = false
                state.paymentConfig = config
                state.isPresentingSheet = true
                return .none

            case let .prepareResponse(.failure(error)):
                state.preparing = false
                state.prepareError = error.localizedDescription
                return .none

            case let .freeRedeemResponse(.success(gameId)):
                state.preparing = false
                state.completedGameId = gameId
                return .send(.delegate(.creatorPaymentConfirmed(gameId: gameId)))

            case let .freeRedeemResponse(.failure(error)):
                state.preparing = false
                state.prepareError = error.localizedDescription
                return .none

            case let .sheetCompleted(result):
                state.isPresentingSheet = false
                switch result {
                case .completed:
                    switch state.context {
                    case .creatorForfait:
                        guard let gameId = state.paymentConfig?.gameId else {
                            state.completionError = "Paiement confirmé mais ID de partie manquant."
                            return .none
                        }
                        state.completedGameId = gameId
                        return .send(.delegate(.creatorPaymentConfirmed(gameId: gameId)))
                    case .hunterCaution:
                        return .send(.delegate(.hunterPaymentConfirmed))
                    }
                case .canceled:
                    state.paymentConfig = nil
                    return .none
                case let .failed(message):
                    state.paymentConfig = nil
                    state.completionError = message
                    return .none
                }

            case .dismissPaymentFailedAlert:
                state.completionError = nil
                return .none

            case .delegate:
                return .none
            }
        }
    }
}

// MARK: - View

struct PaymentView: View {
    @Bindable var store: StoreOf<PaymentFeature>

    var body: some View {
        ZStack {
            Color.gradientBackgroundWarmth.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 24) {
                    headerSection

                    if store.context.showsPromoCode {
                        promoCodeSection
                    }

                    totalSection

                    payButton
                }
                .padding(24)
            }
        }
        .paymentSheetBridge(
            config: store.paymentConfig,
            isPresented: $store.isPresentingSheet,
            onResult: { store.send(.sheetCompleted($0)) }
        )
        .alert(
            "Paiement échoué",
            isPresented: Binding(
                get: { store.completionError != nil },
                set: { if !$0 { store.send(.dismissPaymentFailedAlert) } }
            ),
            actions: {
                Button("OK") { store.send(.dismissPaymentFailedAlert) }
            },
            message: {
                Text(store.completionError ?? "")
            }
        )
    }

    @ViewBuilder private var headerSection: some View {
        VStack(spacing: 8) {
            BangerText("Paiement", size: 38)
                .foregroundStyle(Color.gradientFire)
            Text(headerSubtitle)
                .font(.system(size: 16))
                .foregroundStyle(Color.onBackground.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }

    private var headerSubtitle: String {
        switch store.context {
        case .creatorForfait: return "Finalise la création de ta partie en réglant le forfait."
        case .hunterCaution: return "Verse la caution pour rejoindre cette partie."
        }
    }

    @ViewBuilder private var promoCodeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Code promo (optionnel)")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color.onBackground.opacity(0.7))

            HStack(spacing: 8) {
                TextField("Ex: POULE2026", text: $store.promoCodeInput)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .padding(12)
                    .background(Color.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.onBackground.opacity(0.15), lineWidth: 1)
                    )
                    .disabled(store.validatedPromoCodeId != nil || store.promoValidating)

                if store.validatedPromoCodeId != nil {
                    Button("Retirer") { store.send(.clearPromoTapped) }
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.red)
                } else {
                    Button {
                        store.send(.applyPromoTapped)
                    } label: {
                        if store.promoValidating {
                            ProgressView().controlSize(.small)
                        } else {
                            Text("Appliquer").font(.system(size: 14, weight: .semibold))
                        }
                    }
                    .disabled(store.promoCodeInput.trimmingCharacters(in: .whitespaces).isEmpty || store.promoValidating)
                }
            }

            if let label = store.validatedDiscountLabel {
                Label(label, systemImage: "checkmark.circle.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.green)
            }
            if let error = store.promoError {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(.red)
            }

            Text("Les codes promo sont gérés par Stripe — tu peux les créer/désactiver dans le Stripe Dashboard.")
                .font(.system(size: 11))
                .foregroundStyle(Color.onBackground.opacity(0.5))
        }
        .padding(16)
        .background(Color.surface.opacity(0.4))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder private var totalSection: some View {
        VStack(spacing: 6) {
            if store.freeOverride {
                BangerText("GRATUIT", size: 44)
                    .foregroundStyle(.green)
            } else if let cents = store.paymentConfig?.amountCents {
                BangerText(String(format: "%.2f €", Double(cents) / 100), size: 44)
                    .foregroundStyle(Color.gradientFire)
            } else {
                Text("Le montant sera calculé à l'étape suivante")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.onBackground.opacity(0.6))
            }
            if let error = store.prepareError {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(.red)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color.surface.opacity(0.4))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder private var payButton: some View {
        Button {
            store.send(.payTapped)
        } label: {
            HStack {
                if store.preparing {
                    ProgressView().controlSize(.regular).tint(.white)
                } else {
                    BangerText(store.freeOverride ? "Valider" : "Payer", size: 22)
                        .foregroundStyle(.white)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.gradientFire)
            )
        }
        .disabled(store.preparing || store.isPresentingSheet)
    }
}

// MARK: - PaymentSheet bridge

private struct PaymentSheetBridge: ViewModifier {
    let config: PaymentFeature.PaymentSheetConfig?
    @Binding var isPresented: Bool
    let onResult: (PaymentFeature.PaymentSheetResult) -> Void

    @State private var paymentSheet: PaymentSheet?

    func body(content: Content) -> some View {
        content
            .onChange(of: isPresented) { _, newValue in
                guard newValue, let config else { return }
                presentSheet(for: config)
            }
    }

    private func presentSheet(for config: PaymentFeature.PaymentSheetConfig) {
        var configuration = PaymentSheet.Configuration()
        configuration.merchantDisplayName = "Poule Party"
        configuration.customer = .init(id: config.customerId, ephemeralKeySecret: config.ephemeralKeySecret)
        configuration.allowsDelayedPaymentMethods = false
        configuration.returnURL = "pouleparty://stripe-redirect"
        configuration.applePay = .init(
            merchantId: "merchant.dev.rahier.pouleparty",
            merchantCountryCode: "BE"
        )
        // Force the Name field to appear in PaymentSheet. Bancontact requires
        // `billing_details[name]` at confirmation, and Stripe's `.automatic`
        // mode was not surfacing the field in some Customer scenarios → the
        // user hit a "name required" error with no UI to fill it.
        configuration.billingDetailsCollectionConfiguration.name = .always

        let sheet = PaymentSheet(paymentIntentClientSecret: config.clientSecret, configuration: configuration)
        paymentSheet = sheet

        guard let rootVC = topViewController() else {
            onResult(.failed(message: "Impossible de présenter le paiement."))
            return
        }

        sheet.present(from: rootVC) { result in
            switch result {
            case .completed: onResult(.completed)
            case .canceled: onResult(.canceled)
            case .failed(let error): onResult(.failed(message: error.localizedDescription))
            }
        }
    }

    private func topViewController() -> UIViewController? {
        let keyWindow = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var topController = keyWindow?.rootViewController
        while let presented = topController?.presentedViewController {
            topController = presented
        }
        return topController
    }
}

private extension View {
    func paymentSheetBridge(
        config: PaymentFeature.PaymentSheetConfig?,
        isPresented: Binding<Bool>,
        onResult: @escaping (PaymentFeature.PaymentSheetResult) -> Void
    ) -> some View {
        modifier(PaymentSheetBridge(config: config, isPresented: isPresented, onResult: onResult))
    }
}
