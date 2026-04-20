export default {
  nav: {
    register: "Register",
    privacy: "Privacy",
    terms: "Terms",
    support: "Support",
  },
  home: {
    tagline:
      "A real-world GPS hide-and-seek game. One player is the Chicken, the rest are Hunters. The zone shrinks, the hunt intensifies!",
    cta: "Next event: April 23!",
    appComingSoon: "App coming soon...",
    downloadApp: "Download the app!",
    appStore: "App Store",
    googlePlay: "Google Play",
    androidDisclaimer: "⚠️ Android: the app is in closed testing. Your email must be registered with the developer before you can download it. If it doesn't work, let us know on the WhatsApp group!",
  },
  privacy: {
    title: "Privacy Policy",
    lastUpdated: "Last updated: March 4, 2026",
    overview: "Data Controller",
    overviewText:
      'Poule Party ("we", "our", "the app") is a location-based mobile game. The data controller responsible for your personal data is:',
    controllerDetails: "Julien Rahier, julien@rahier.dev, Belgium",
    dataCollected: "Data We Collect",
    locationData: "Location data:",
    locationDataText:
      "Your GPS coordinates are collected while a game is in progress and shared with other players in your game session. Location tracking stops when the game ends.",
    auth: "Anonymous authentication:",
    authText:
      "We use Firebase Anonymous Authentication to generate an anonymous user ID for your device during gameplay. No email, name, or personal account is required.",
    playerName: "Player name:",
    playerNameText:
      "The display name you choose when joining a game. This is stored locally on your device and shared with other players during a game session. It may also be recorded in game results (e.g. winners). It is not linked to any personal identity.",
    analytics: "Analytics:",
    analyticsText:
      "We use Firebase Analytics to collect anonymous usage data (app opens, screen views) and Firebase Crashlytics for crash reports, to improve the app. No personally identifiable information is collected through these services.",
    legalBasis: "Legal Basis for Processing",
    legalBasisIntro: "Under the GDPR (Art. 6), we process your data based on the following legal grounds:",
    legalBasisConsent: "Consent (Art. 6(1)(a)):",
    legalBasisConsentText: "Location data: you explicitly grant location permission before any data is collected. You can withdraw consent at any time by revoking location permissions in your device settings.",
    legalBasisLegitimate: "Legitimate interest (Art. 6(1)(f)):",
    legalBasisLegitimateText: "Anonymous authentication and crash reporting: necessary for the app to function and to maintain service quality. Our legitimate interest does not override your rights, as the data is fully anonymous.",
    howWeUse: "How We Use Your Data",
    howWeUse1:
      "Location data is used solely for real-time gameplay mechanics (showing positions on the map, zone detection).",
    howWeUse2:
      "Location data is stored in Firebase Firestore during the game. Game data (including location history) may be retained after the game ends for operational purposes and can be deleted upon request.",
    howWeUse3:
      "Analytics data is used to understand app usage patterns and fix bugs.",
    thirdParties: "Third-Party Services",
    thirdPartiesIntro: "We use the following third-party services to operate the app. Each has its own privacy policy:",
    thirdPartyFirebaseAnalytics: "Google Analytics for Firebase",
    thirdPartyFirebaseAnalyticsUrl: "https://firebase.google.com/support/privacy",
    thirdPartyCrashlytics: "Firebase Crashlytics",
    thirdPartyCrashlyticsUrl: "https://firebase.google.com/support/privacy/",
    thirdPartyMapbox: "Mapbox",
    thirdPartyMapboxUrl: "https://www.mapbox.com/legal/privacy",
    dataSharing: "Data Sharing",
    dataSharingText:
      "We do not sell, trade, or share your personal data with third parties for marketing purposes. Location data is only shared with other players in the same game session during active gameplay.",
    internationalTransfers: "International Data Transfers",
    internationalTransfersText:
      "Your data is processed by Google Firebase services, whose servers may be located outside the European Economic Area (EEA), including in the United States. Google operates under the EU-US Data Privacy Framework and Standard Contractual Clauses (SCCs) to ensure an adequate level of data protection as required by the GDPR. Mapbox also processes map data under similar safeguards.",
    dataRetention: "Data Retention",
    dataRetentionText:
      "Game data (including location history) may be retained in Firebase Firestore after a game ends. No long-term location tracking occurs outside of active gameplay. You can request deletion of your game data by contacting us at julien@rahier.dev. Anonymous authentication tokens may persist on your device but can be cleared by deleting your account in the app settings or by uninstalling the app.",
    children: "Children's Privacy",
    childrenText:
      "The app is not directed at children under 16. We do not knowingly collect personal information from children under 16. If you believe a child under 16 has provided us with personal data, please contact us so we can delete it.",
    rights: "Your Rights Under the GDPR",
    rightsIntro: "As a user in the European Economic Area, you have the following rights:",
    rightAccess: "Right of access (Art. 15):",
    rightAccessText: "You can request a copy of the personal data we hold about you.",
    rightRectification: "Right to rectification (Art. 16):",
    rightRectificationText: "You can request correction of inaccurate data.",
    rightErasure: "Right to erasure (Art. 17):",
    rightErasureText: "You can request deletion of your data. You can delete your anonymous authentication account directly in the app via Settings > Delete My Data. For complete deletion of game data stored in Firestore, please contact us at julien@rahier.dev.",
    rightRestriction: "Right to restriction (Art. 18):",
    rightRestrictionText: "You can request that we limit the processing of your data.",
    rightPortability: "Right to data portability (Art. 20):",
    rightPortabilityText: "You can request your data in a structured, machine-readable format.",
    rightObject: "Right to object (Art. 21):",
    rightObjectText: "You can object to the processing of your data based on legitimate interest.",
    rightWithdraw: "Right to withdraw consent:",
    rightWithdrawText: "You can withdraw your consent for location tracking at any time by revoking location permissions in your device settings.",
    rightsExercise: "To exercise any of these rights, contact us at julien@rahier.dev. We will respond within 30 days.",
    supervisory: "Supervisory Authority",
    supervisoryText:
      "If you believe your data protection rights have been violated, you have the right to lodge a complaint with your local supervisory authority. In Belgium, this is the:",
    supervisoryAuthority: "Autorité de protection des données (APD)",
    supervisoryUrl: "https://www.autoriteprotectiondonnees.be",
    contact: "Contact",
    contactText: "If you have questions about this privacy policy, please contact us at",
  },
  support: {
    title: "Support",
    needHelp: "Need Help?",
    needHelpText:
      "If you are experiencing issues with Poule Party or have any questions, we are here to help.",
    commonIssues: "Common Issues",
    locationTitle: "Location not working",
    locationText:
      'Make sure you have granted location permissions to Poule Party in your device settings. The app requires "While Using" or "Always" location access to function during gameplay.',
    joinTitle: "Cannot join a game",
    joinText:
      "Verify that the game code is correct (6 characters). The game might have already started or ended. Ask the game creator for a new code.",
    mapTitle: "Map not loading",
    mapText:
      "Ensure you have a stable internet connection. Try closing and reopening the app.",
    contactTitle: "Contact Us",
    contactText:
      "For bug reports, feature requests, or any other questions, reach out to us at",
    contactFooter:
      "Please include your device model, OS version, and a description of the issue so we can help you faster.",
  },
  terms: {
    title: "Terms of Use",
    lastUpdated: "Last updated: March 4, 2026",
    acceptance: "Acceptance of Terms",
    acceptanceText:
      "By downloading, installing or using Poule Party, you agree to these Terms of Use. If you do not agree, please do not use the app.",
    description: "Description of the Service",
    descriptionText:
      "Poule Party is a free location-based mobile game where one player (the Chicken) hides while other players (Hunters) try to find them using a real-time map with a shrinking zone.",
    userConduct: "User Conduct",
    conduct1: "Use the app only for its intended purpose (playing the game).",
    conduct2: "Do not attempt to cheat, hack, or reverse-engineer the app.",
    conduct3: "Respect other players and play in safe, public locations.",
    conduct4: "You are responsible for your own safety while playing.",
    location: "Location Data",
    locationText:
      "The app requires location access to function. Your location is shared with other players in your game session only during active gameplay. See our Privacy Policy for details.",
    disclaimer: "Disclaimer",
    disclaimerText:
      'The app is provided "as is" without warranties of any kind. We do not guarantee uninterrupted or error-free service.',
    liability: "Limitation of Liability",
    liabilityText:
      "Julien Rahier shall not be liable for any damages arising from the use of the app, including but not limited to physical injury, property damage, or data loss.",
    termination: "Termination",
    terminationText:
      "We reserve the right to suspend or terminate access to the app at any time, without notice, for conduct that violates these terms.",
    changes: "Changes to These Terms",
    changesText:
      "We may update these terms from time to time. Continued use of the app after changes constitutes acceptance of the new terms.",
    contact: "Contact",
    contactText: "If you have questions about these terms, please contact us at",
  },
  register: {
    date: "April 23, 2026 at 7:00 PM",
    intro:
      "Get ready for a totally unique experience: a life-size chicken hunt, right in the heart of the city.",
    description:
      "Inspired by urban games like \"hide-and-seek meets treasure hunt\", Poule Party is a collective adventure where strategy, speed, and creativity make all the difference.",
    conceptTitle: "🎯 The concept",
    conceptText:
      "A \"Chicken\" is released into the city with a few minutes head start…",
    mission: "Your mission? Find it before the other teams.",
    zoneText:
      "Thanks to an evolving location system, a zone around the Chicken appears and gradually shrinks… It's up to you to get closer, investigate, and strike at the right moment.",
    butWait: "But that's not all 👇",
    pointsTitle: "🏆 Earn points (and glory)",
    pointsList: [
      "🧭 Find the Chicken before the others",
      "📸 Complete fun and quirky challenges",
      "🎭 Show creativity (and boldness)",
      "😈 (Optional) Sabotage the other teams",
    ],
    pointsOutro:
      "Every action earns you points… and in the end, only one team will be crowned the winner.",
    whyTitle: "🎉 Why participate?",
    whyList: [
      "A unique and super fun activity",
      "Perfect for meeting new people",
      "Accessible to everyone (no need to be athletic)",
      "Unforgettable memories (and often hilarious ones)",
    ],
    infoTitle: "📅 Practical info",
    infoList: [
      "📆 Date: April 23 at 7 PM",
      "👥 Limited spots: 35 participants",
      "🎮 Format: teams + outdoor game",
      "⏱ Duration: ± 2 hours of gameplay",
      "📍 Starting point: shared after registration",
    ],
    spotsLeft: "{0}/{1} registered",
    formTitle: "Register",
    firstNameLabel: "First name",
    firstNamePlaceholder: "John",
    lastNameLabel: "Last name",
    lastNamePlaceholder: "Doe",
    emailLabel: "Email",
    emailHint: "📱 On Android, use your Play Store email: that's the one that unlocks the app (closed testing)",
    emailPlaceholder: "john@example.com",
    gsmLabel: "Phone number",
    gsmHint: "To add you to the event's WhatsApp group",
    gsmPlaceholder: "+32 470 12 34 56",
    emailError: "Invalid email address",
    gsmTooShort: "{0} digit(s) missing",
    gsmTooLong: "{0} digit(s) too many",
    gsmFormatError: "Number must start with + or 0",
    willingToPayLabel: "How much would you be willing to pay to participate?",
    willingToPayPlaceholder: "E.g. 5€, 10€, free…",
    commentLabel: "Any comments? (optional)",
    commentHint: "Ideas, suggestions, recommendations…",
    commentPlaceholder: "E.g. power-up idea, question about the event…",
    submitButton: "Sign me up!",
    submitting: "Registering…",
    duplicateText: "This email is already registered! If this is a mistake, contact us at julien@rahier.dev.",
    fullText: "Sorry, all spots are taken!",
    errorText: "Something went wrong. Please try again!",
    successTitle: "Registration confirmed!",
    successText:
      "You'll receive an email with all the details before the event. Get ready for the hunt!",
  },
  footer: {
    rights: "All rights reserved.",
  },
};
