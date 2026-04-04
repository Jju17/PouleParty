//
//  Color+Utils.swift
//  PouleParty
//
//  Created by Julien Rahier on 17/03/2024.
//

import SwiftUI

extension Color {
    // MARK: - Core Brand
    static let CRBeige = Color("CRBeige")
    static let CRBeigeWarm = Color(hex: 0xFFE8C8)
    static let CROrange = Color(hex: 0xFE6A00)
    static let CRPink = Color(hex: 0xEF0778)

    // MARK: - Game Roles
    static let chickenYellow = Color(hex: 0xFFD23F)
    static let hunterRed = Color(hex: 0xC41E45)

    // MARK: - Zone & Map
    static let zoneGreen = Color(hex: 0x39FF14)
    static let zoneDanger = Color(hex: 0xCC0530)

    // MARK: - Power-ups (Neon Family)
    static let powerupStealth = Color(hex: 0xBF40BF)
    static let powerupFreeze = Color(hex: 0x00F5FF)
    static let powerupRadar = Color(hex: 0xFF6F3C)
    static let powerupVision = Color(hex: 0x4D96FF)
    static let powerupSpeed = Color(hex: 0xDFFF00)
    static let powerupShield = Color(hex: 0xFFD700)

    // MARK: - Semantic
    static let success = Color(hex: 0x39FF14)
    static let danger = Color(hex: 0xCC0530)
    static let warning = Color(hex: 0xFFD23F)
    static let info = Color(hex: 0x4D96FF)

    // MARK: - Dark Mode
    static let darkBackground = Color(hex: 0x1A1A2E)
    static let darkSurface = Color(hex: 0x16213E)

    // MARK: - Adaptive Theme Colors
    static let background = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(Color(hex: 0x1A1A2E))
            : UIColor(Color(hex: 0xFDF9D5))
    })

    static let surface = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(Color(hex: 0x16213E))
            : UIColor.white
    })

    static let onBackground = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor.white
            : UIColor.black
    })

    static let onSurface = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor.white
            : UIColor.black
    })

    static let primaryColor = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(Color(hex: 0xFF8C33))
            : UIColor(Color(hex: 0xFE6A00))
    })

    static let secondaryColor = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(Color(hex: 0xF54D9E))
            : UIColor(Color(hex: 0xEF0778))
    })

    static let CRBeigeWarmAdaptive = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(Color(hex: 0x0F0F23))
            : UIColor(Color(hex: 0xFFE8C8))
    })

    // MARK: - Hex Initializer
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }

    // MARK: - Gradients
    static let gradientFire = LinearGradient(
        colors: [.CROrange, .CRPink],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let gradientFree = LinearGradient(
        colors: [Color(hex: 0x0EA5E9), Color(hex: 0x0369A1)],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let gradientDeposit = LinearGradient(
        colors: [Color(hex: 0x8B5CF6), Color(hex: 0x6D28D9)],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let gradientChicken = LinearGradient(
        colors: [.chickenYellow, .CROrange],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let gradientHunter = LinearGradient(
        colors: [.hunterRed, .CRPink],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let gradientBackgroundWarmth = RadialGradient(
        colors: [.background, .CRBeigeWarmAdaptive],
        center: .center,
        startRadius: 0,
        endRadius: 500
    )
}

// MARK: - Neon Glow Modifier
struct NeonGlow: ViewModifier {
    let color: Color
    let intensity: GlowIntensity

    enum GlowIntensity {
        case subtle, medium, intense
    }

    func body(content: Content) -> some View {
        switch intensity {
        case .subtle:
            content.shadow(color: color.opacity(0.4), radius: 4)
        case .medium:
            content
                .shadow(color: color.opacity(0.6), radius: 7)
                .shadow(color: color.opacity(0.3), radius: 14)
        case .intense:
            content
                .shadow(color: color.opacity(0.8), radius: 7)
                .shadow(color: color.opacity(0.5), radius: 14)
                .shadow(color: color.opacity(0.3), radius: 28)
        }
    }
}

extension View {
    func neonGlow(_ color: Color, intensity: NeonGlow.GlowIntensity = .medium) -> some View {
        modifier(NeonGlow(color: color, intensity: intensity))
    }
}
