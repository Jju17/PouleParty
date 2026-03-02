export default {
  nav: {
    privacy: "Privacy",
    support: "Support",
  },
  home: {
    tagline:
      "A real-world GPS hide-and-seek game. One player is the Chicken, the rest are Hunters. The zone shrinks, the hunt intensifies!",
    appStore: "App Store",
    googlePlay: "Google Play",
  },
  privacy: {
    title: "Privacy Policy",
    lastUpdated: "Last updated: March 1, 2026",
    overview: "Overview",
    overviewText:
      'Poule Party ("we", "our", "the app") is a location-based mobile game developed by Julien Rahier. This policy explains how we collect, use and protect your information.',
    dataCollected: "Data We Collect",
    locationData: "Location data:",
    locationDataText:
      "Your GPS coordinates are collected while a game is in progress and shared with other players in your game session. Location tracking stops when the game ends.",
    auth: "Anonymous authentication:",
    authText:
      "We use Firebase Anonymous Authentication to identify your device during gameplay. No email, name, or personal account is required.",
    playerName: "Player name:",
    playerNameText:
      "The display name you choose when joining a game. This is not linked to any personal identity.",
    analytics: "Analytics:",
    analyticsText:
      "We use Firebase Analytics to collect anonymous usage data (app opens, screen views, crash reports) to improve the app. No personally identifiable information is collected.",
    howWeUse: "How We Use Your Data",
    howWeUse1:
      "Location data is used solely for real-time gameplay mechanics (showing positions on the map, zone detection).",
    howWeUse2:
      "Location data is stored temporarily in Firebase Firestore for the duration of the game and is not retained after the game ends.",
    howWeUse3:
      "Analytics data is used to understand app usage patterns and fix bugs.",
    dataSharing: "Data Sharing",
    dataSharingText:
      "We do not sell, trade, or share your personal data with third parties. Location data is only shared with other players in the same game session. We use Google Firebase services (Firestore, Auth, Analytics) to operate the app; their privacy policy applies to the data processed by their services.",
    dataRetention: "Data Retention",
    dataRetentionText:
      "Game data (including location history) is automatically deleted after the game ends. No long-term location history is stored. Anonymous authentication tokens may persist on your device but can be cleared by uninstalling the app.",
    children: "Children's Privacy",
    childrenText:
      "The app is not directed at children under 13. We do not knowingly collect personal information from children under 13.",
    rights: "Your Rights",
    rightsText:
      "You can stop sharing your location at any time by closing the app or revoking location permissions in your device settings. You can request deletion of any data associated with your device by contacting us.",
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
  footer: {
    rights: "All rights reserved.",
  },
};
