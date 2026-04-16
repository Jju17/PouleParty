//
//  GameCreationHelpers.swift
//  PouleParty
//
//  Reusable building blocks for the game-creation wizard.
//

import Foundation
import SwiftUI

// MARK: - Step Header

struct StepHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 8) {
            BangerText(title, size: 28)
                .foregroundStyle(Color.onBackground)
                .multilineTextAlignment(.center)
            Text(subtitle)
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.onBackground.opacity(0.6))
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 24)
    }
}

// MARK: - Selection Card

struct SelectionCard: View {
    let title: String
    let emoji: String
    let subtitle: String
    let isSelected: Bool
    let gradient: LinearGradient
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Text(emoji)
                    .font(.system(size: 36))

                VStack(alignment: .leading, spacing: 4) {
                    BangerText(title, size: 22)
                        .foregroundStyle(isSelected ? .black : Color.onBackground)
                    Text(subtitle)
                        .font(.gameboy(size: 7))
                        .foregroundStyle(isSelected ? .black.opacity(0.7) : Color.onBackground.opacity(0.6))
                }

                Spacer()

                Image(systemName: "checkmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.black)
                    .opacity(isSelected ? 1 : 0)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(isSelected ? AnyShapeStyle(gradient) : AnyShapeStyle(Color.surface))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? Color.clear : Color.onBackground.opacity(0.2), lineWidth: 1)
            )
            .shadow(color: isSelected ? Color.CROrange.opacity(0.3) : .clear, radius: 8, y: 4)
        }
    }
}

// MARK: - Recap Row

struct RecapRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground.opacity(0.6))
            Spacer()
            Text(value)
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Formatters

enum GameCreationFormatters {
    static func date(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    static func duration(_ minutes: Double) -> String {
        let hours = Int(minutes) / 60
        let mins = Int(minutes) % 60
        if mins == 0 {
            return "\(hours)h"
        }
        return "\(hours)h\(String(format: "%02d", mins))"
    }

    static func registrationDeadlineLabel(_ minutes: Int) -> String {
        switch minutes {
        case ..<60: return "\(minutes) min before"
        case 60: return "1 hour before"
        case 1440: return "1 day before"
        default: return "\(minutes / 60) hours before"
        }
    }
}
