//
//  RecapStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct RecapStep: GameCreationStepView {
    static let step: GameCreationStep = .recap
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                VStack(spacing: 20) {
                    StepHeader(
                        title: "Ready to go!",
                        subtitle: "Review your game settings"
                    )

                    gameCodeCard

                    settingsSummary

                    if !store.isZoneConfigured {
                        Text(store.currentGame.gameMode == .stayInTheZone
                             ? "Set a start zone and final zone to start"
                             : "Set a start zone to start")
                            .font(.gameboy(size: 8))
                            .foregroundStyle(Color.CROrange)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 16)
                    }

                    Spacer().frame(height: 20)
                }
                .frame(minHeight: geo.size.height)
            }
        }
    }

    private var gameCodeCard: some View {
        VStack(spacing: 8) {
            Text("GAME CODE")
                .font(.gameboy(size: 8))
                .foregroundStyle(Color.onBackground.opacity(0.6))
            BangerText(store.currentGame.gameCode, size: 40)
                .foregroundStyle(Color.CROrange)
        }
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.surface)
        )
        .padding(.horizontal, 24)
    }

    private var settingsSummary: some View {
        VStack(spacing: 0) {
            RecapRow(label: "Role", value: store.isParticipating ? "Chicken 🐔" : "Organizer 📋")
            Divider()
            RecapRow(label: "Mode", value: store.currentGame.gameMode.title)
            Divider()
            RecapRow(label: "Max Players", value: "\(store.currentGame.maxPlayers)")
            Divider()
            RecapRow(label: "Start", value: GameCreationFormatters.date(store.currentGame.startDate))
            Divider()
            RecapRow(label: "Duration", value: GameCreationFormatters.duration(store.gameDurationMinutes))
            Divider()
            let endDate = store.currentGame.startDate.addingTimeInterval(store.gameDurationMinutes * 60)
            RecapRow(label: "End", value: GameCreationFormatters.date(endDate))
            Divider()
            RecapRow(label: "Head Start", value: "\(Int(store.currentGame.timing.headStartMinutes)) min")
            Divider()
            RecapRow(label: "Zone Radius", value: "\(Int(store.currentGame.zone.radius)) m")
            Divider()
            RecapRow(label: "Power-Ups", value: store.currentGame.powerUps.enabled ? "Enabled" : "Disabled")
            if store.currentGame.powerUps.enabled {
                Divider()
                let enabledNames = PowerUp.PowerUpType.allCases
                    .filter { store.currentGame.powerUps.enabledTypes.contains($0.rawValue) }
                    .map(\.displayName)
                    .joined(separator: ", ")
                RecapRow(label: "Active Types", value: enabledNames)
            }
            if store.currentGame.gameMode == .followTheChicken {
                Divider()
                RecapRow(label: "Chicken sees hunters", value: store.currentGame.chickenCanSeeHunters ? "Yes" : "No")
            }
            if store.currentGame.isPaid {
                Divider()
                RecapRow(label: "Pricing", value: store.currentGame.pricing.model.title)
                Divider()
                let totalCents = store.currentGame.pricing.pricePerPlayer * store.currentGame.maxPlayers
                RecapRow(label: "Total price", value: String(format: "%.2f€", Double(totalCents) / 100.0))
                if store.currentGame.pricing.deposit > 0 {
                    Divider()
                    RecapRow(label: "Deposit", value: String(format: "%.2f€", Double(store.currentGame.pricing.deposit) / 100.0))
                }
            }
            Divider()
            RecapRow(label: "Registration", value: store.currentGame.registration.required ? "Required" : "Open")
            if store.currentGame.registration.required, let minutes = store.currentGame.registration.closesMinutesBefore {
                Divider()
                RecapRow(label: "Registration closes", value: GameCreationFormatters.registrationDeadlineLabel(minutes))
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.surface)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 24)
    }
}
