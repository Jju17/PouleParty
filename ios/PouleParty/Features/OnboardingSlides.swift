//
//  OnboardingSlides.swift
//  PouleParty
//
//  Created by Julien Rahier on 14/02/2026.
//

import CoreLocation
import SwiftUI
import UIKit
import UserNotifications

// MARK: - Generic Slide Layout

struct OnboardingSlideLayout<Icon: View, Extra: View>: View {
    let title: String
    let subtitle: String?
    let titleSize: CGFloat
    @ViewBuilder let icon: () -> Icon
    @ViewBuilder let extraContent: () -> Extra

    init(
        title: String,
        subtitle: String? = nil,
        titleSize: CGFloat = 32,
        @ViewBuilder icon: @escaping () -> Icon,
        @ViewBuilder extraContent: @escaping () -> Extra = { EmptyView() }
    ) {
        self.title = title
        self.subtitle = subtitle
        self.titleSize = titleSize
        self.icon = icon
        self.extraContent = extraContent
    }

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            icon()

            BangerText(title, size: titleSize)
                .multilineTextAlignment(.center)
                .foregroundStyle(.black)

            if let subtitle {
                BangerText(subtitle, size: 18)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.black.opacity(0.6))
                    .padding(.horizontal, 10)
            }

            extraContent()

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 40)
    }
}

// MARK: - Slide 1: Welcome

struct OnboardingWelcomeSlide: View {
    var body: some View {
        OnboardingSlideLayout(
            title: "Welcome to\nPouleParty!",
            subtitle: "The ultimate real-world\nhide-and-seek game",
            titleSize: 36
        ) {
            Image("logo")
                .resizable()
                .scaledToFit()
                .frame(width: 160, height: 160)
        }
    }
}

// MARK: - Slide 2: Nominate a Chicken

struct OnboardingChickenSlide: View {
    var body: some View {
        OnboardingSlideLayout(
            title: "Nominate a Chicken",
            subtitle: "Pick the Stag, the Birthday Girl, or whoever just really wants to be a Chicken.\n\nTheir job is to hide."
        ) {
            Text("🐔")
                .font(.system(size: 80))
        }
    }
}

// MARK: - Slide 3: Hunt Them Down

struct OnboardingHuntSlide: View {
    var body: some View {
        OnboardingSlideLayout(
            title: "Hunt Them Down",
            subtitle: "Split into squads. Use the map to track them.\n\nThe Chicken could be hiding anywhere!"
        ) {
            Text("🗺️")
                .font(.system(size: 80))
        }
    }
}

// MARK: - Slide 4: Location Permission

struct OnboardingLocationSlide: View {
    let status: CLAuthorizationStatus
    let onRequestWhenInUse: () -> Void
    let onRequestAlways: () -> Void

    private var emoji: String {
        switch status {
        case .authorizedAlways: "📍"
        case .authorizedWhenInUse: "👀"
        default: "😢"
        }
    }

    var body: some View {
        OnboardingSlideLayout(title: "We Need Your Location") {
            Text(emoji)
                .font(.system(size: 80))
        } extraContent: {
            switch status {
            case .authorizedAlways:
                Group {
                    BangerText("You're all set! The game will track location even in the background.\n\nMaximum fun guaranteed!", size: 18)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.black.opacity(0.6))
                        .padding(.horizontal, 10)

                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                            .font(.system(size: 24))
                        BangerText("Always allowed!", size: 20)
                            .foregroundStyle(.green)
                    }
                    .padding(.top, 8)
                }

            case .authorizedWhenInUse:
                Group {
                    BangerText("Almost there! The game needs to track the Chicken even when the app is in the background.\n\nPlease select \"Always Allow\" so the Hunters can find you!", size: 18)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.black.opacity(0.6))
                        .padding(.horizontal, 10)

                    Button {
                        onRequestAlways()
                    } label: {
                        BangerText("Allow Always", size: 20)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 28)
                            .padding(.vertical, 14)
                            .background(Capsule().fill(Color.CROrange))
                    }
                    .padding(.top, 8)
                }

            case .denied, .restricted:
                Group {
                    BangerText("Location access was denied.\nPlease enable it in Settings to play.", size: 18)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.black.opacity(0.6))
                        .padding(.horizontal, 10)

                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        BangerText("Open Settings", size: 18)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 10)
                            .background(Capsule().fill(Color.CROrange))
                    }
                    .padding(.top, 8)
                }

            default:
                Group {
                    BangerText("Without it, the game can't work.\nNo location = no map = no fun.\n\nWe promise we only use it during the game!", size: 18)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.black.opacity(0.6))
                        .padding(.horizontal, 10)

                    Button {
                        onRequestWhenInUse()
                    } label: {
                        BangerText("Allow Location Access", size: 20)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 28)
                            .padding(.vertical, 14)
                            .background(Capsule().fill(Color.CROrange))
                    }
                    .padding(.top, 8)
                }
            }
        }
    }
}

// MARK: - Slide 5: Notifications

struct OnboardingNotificationSlide: View {
    let status: UNAuthorizationStatus
    let onRequestPermission: () -> Void

    private var emoji: String {
        switch status {
        case .authorized, .provisional, .ephemeral: "🔔"
        case .denied: "🔕"
        default: "🔔"
        }
    }

    var body: some View {
        OnboardingSlideLayout(title: String(localized: "Stay in the Loop")) {
            Text(emoji)
                .font(.system(size: 80))
        } extraContent: {
            switch status {
            case .authorized, .provisional, .ephemeral:
                Group {
                    Text("Notifications enabled! You'll know when the game starts, the zone shrinks, and when hunters find the chicken.")
                        .font(.banger(size: 18))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.black.opacity(0.6))
                        .padding(.horizontal, 10)

                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                            .font(.system(size: 24))
                        Text("Notifications enabled!")
                            .font(.banger(size: 20))
                            .foregroundStyle(.green)
                    }
                    .padding(.top, 8)
                }

            case .denied:
                Group {
                    Text("Notifications were denied. You can enable them in Settings to get game alerts.")
                        .font(.banger(size: 18))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.black.opacity(0.6))
                        .padding(.horizontal, 10)

                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        Text("Open Settings")
                            .font(.banger(size: 18))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 10)
                            .background(Capsule().fill(Color.CROrange))
                    }
                    .padding(.top, 8)
                }

            default:
                Group {
                    Text("Get notified when the game starts, the zone shrinks, and when a hunter finds the chicken!\n\nThis is optional but recommended.")
                        .font(.banger(size: 18))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.black.opacity(0.6))
                        .padding(.horizontal, 10)

                    Button {
                        onRequestPermission()
                    } label: {
                        Text("Enable Notifications")
                            .font(.banger(size: 20))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 28)
                            .padding(.vertical, 14)
                            .background(Capsule().fill(Color.CROrange))
                    }
                    .padding(.top, 8)
                }
            }
        }
    }
}

// MARK: - Slide 6: Nickname

struct OnboardingNicknameSlide: View {
    @Binding var nickname: String
    let maxLength: Int

    var body: some View {
        OnboardingSlideLayout(
            title: "Choose Your Nickname",
            subtitle: "This is how other players\nwill see you in the game."
        ) {
            Text("🏷️")
                .font(.system(size: 80))
        } extraContent: {
            VStack(spacing: 8) {
                TextField("Your nickname", text: $nickname)
                    .font(.banger(size: 22))
                    .foregroundStyle(.black)
                    .multilineTextAlignment(.center)
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.black.opacity(0.2), lineWidth: 1)
                    )
                    .padding(.horizontal, 20)

                BangerText("\(nickname.count)/\(maxLength)", size: 14)
                    .foregroundStyle(.black.opacity(0.4))
            }
        }
    }
}

// MARK: - Slide 7: Ready

struct OnboardingReadySlide: View {
    var body: some View {
        OnboardingSlideLayout(
            title: "Ready to Play?",
            subtitle: "Close in on the Chicken. The zone shrinks, the hunt intensifies.\n\nFind the Chicken before time runs out!"
        ) {
            Text("🎉")
                .font(.system(size: 80))
        }
    }
}
