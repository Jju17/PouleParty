export default {
  nav: {
    privacy: "Privacy",
    terms: "Terms",
    support: "Support",
  },
  home: {
    tagline:
      "A real-world GPS hide-and-seek game. One player is the Chicken, the rest are Hunters. The zone shrinks, the hunt intensifies!",
    appComingSoon: "App coming soon...",
    downloadApp: "Download the app!",
    appStore: "App Store",
    googlePlay: "Google Play",
    androidDisclaimer: "⚠️ Android: the app is in closed testing. Your email must be registered with the developer before you can download it. If it doesn't work, let us know on the WhatsApp group!",
    dDayEyebrow: "PouleParty D-Day",
    dDayTitle: "🐔 Saturday, June 6",
    dDayBody: "A Chicken hides in Ixelles. Your team has to hunt them down. €12 / player · Teams of 3 to 5.",
    dDayCta: "REGISTER YOUR TEAM →",
  },
  privacy: {
    title: "Privacy Policy",
    lastUpdated: "Last updated: May 18, 2026",
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
    paidEventData: "Paid event registration:",
    paidEventDataText:
      "When you register for a paid PouleParty event on the web form (pouleparty.be/registration), we collect: the captain's full name, team name, email address, phone number, team size, your IP address and locale, the Stripe Checkout session ID, and a 6-character validation code we generate for the event. This data is stored in our `/eventRegistrations` Firestore collection and transmitted to the third-party services listed below for payment, confirmation email delivery, and on-site logistics. Payment card details are never collected by us — they are entered directly on Stripe's hosted Checkout page.",
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
    thirdPartyStripe: "Stripe (payment processing for paid events — Stripe Technology Europe Ltd in Ireland, with onward transfer to Stripe Inc. in the US under Standard Contractual Clauses)",
    thirdPartyStripeUrl: "https://stripe.com/privacy",
    thirdPartyResend: "Resend (transactional confirmation emails for paid events — Resend Inc. in the US under Standard Contractual Clauses)",
    thirdPartyResendUrl: "https://resend.com/legal/privacy-policy",
    thirdPartyGoogleSheets: "Google Sheets (operational roster for on-site D-Day check-in — Google LLC under the EU-US Data Privacy Framework)",
    thirdPartyGoogleSheetsUrl: "https://policies.google.com/privacy",
    dataSharing: "Data Sharing",
    dataSharingText:
      "We do not sell, trade, or share your personal data with third parties for marketing purposes. Location data is only shared with other players in the same game session during active gameplay.",
    internationalTransfers: "International Data Transfers",
    internationalTransfersText:
      "Your data is processed by Google Firebase services, whose servers may be located outside the European Economic Area (EEA), including in the United States. Google operates under the EU-US Data Privacy Framework and Standard Contractual Clauses (SCCs) to ensure an adequate level of data protection as required by the GDPR. Mapbox processes map data under similar safeguards. For paid event registrations: Stripe routes payments via Stripe Technology Europe Ltd (Ireland) and transmits anonymized risk/fraud signals to Stripe Inc. (USA) under SCCs. Resend (USA) transmits the confirmation email under SCCs. Google Sheets data (event roster) is stored in Google LLC (USA) infrastructure under the EU-US Data Privacy Framework.",
    dataRetention: "Data Retention",
    dataRetentionText:
      "Game data (including location history) may be retained in Firebase Firestore after a game ends. No long-term location tracking occurs outside of active gameplay. Paid event registrations in `/eventRegistrations` are kept for 12 months after the event date for accounting, dispute resolution and tax obligations, then purged. The Google Sheet used for on-site check-in is purged within 30 days after the event. You can request deletion of your game data by contacting us at julien@rahier.dev. Anonymous authentication tokens may persist on your device but can be cleared by deleting your account in the app settings or by uninstalling the app.",
    cookies: "Cookies and Similar Technologies",
    cookiesText:
      "The public website uses strictly-necessary cookies only: Firebase App Check for bot protection on the registration form, and Stripe-hosted cookies on the Stripe Checkout page itself (handled by Stripe, see their privacy policy). We do not use any advertising, analytics or tracking cookies on the public web pages. No cookie banner is shown because consent under the ePrivacy Directive is not required for strictly-necessary cookies.",
    children: "Age Requirements",
    childrenText:
      "The free PouleParty mobile game is recommended for ages 13 and above; users under 13 must have a parent or guardian set up the device and grant location permissions on their behalf. The paid PouleParty D-Day event held in public venues (bars, the city of Brussels) is reserved for participants aged 18 and above due to the venue's licensing requirements. We do not knowingly collect personal information from children under 13 through the app, or from anyone under 18 through the paid event registration form. If you believe a minor has provided us with data outside these limits, please contact us at julien@rahier.dev so we can delete it.",
    rights: "Your Rights Under the GDPR",
    rightsIntro: "As a user in the European Economic Area, you have the following rights:",
    rightAccess: "Right of access (Art. 15):",
    rightAccessText: "You can request a copy of the personal data we hold about you.",
    rightRectification: "Right to rectification (Art. 16):",
    rightRectificationText: "You can request correction of inaccurate data.",
    rightErasure: "Right to erasure (Art. 17):",
    rightErasureText: "You can request deletion of your data. The in-app Settings > Delete Account button deletes your anonymous Firebase Auth account and the `/users/{uid}` profile document immediately. Games you participated in (and the team name you used for them) are kept indefinitely for game-history integrity — they are visible only to participants of the same session and the game creator. For full scrubbing of past game data (including your team name in winners arrays), contact us at julien@rahier.dev and we will process the manual scrub within 30 days. Paid event registrations are deleted on request (subject to the 12-month accounting retention period mentioned above).",
    rightRestriction: "Right to restriction (Art. 18):",
    rightRestrictionText: "You can request that we limit the processing of your data.",
    rightPortability: "Right to data portability (Art. 20):",
    rightPortabilityText: "You can request your data in a structured, machine-readable format. Send a request to julien@rahier.dev and we will respond within 30 days with a JSON export containing your `/users/{uid}` profile document, the team names and winner records linked to your anonymous user ID across past games, and any paid event registration tied to your email if applicable.",
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
    lastUpdated: "Last updated: May 18, 2026",
    acceptance: "Acceptance of Terms",
    acceptanceText:
      "By downloading, installing or using Poule Party, or by registering for a paid PouleParty event, you agree to these Terms of Use. If you do not agree, please do not use the app or register for an event.",
    description: "Description of the Service",
    descriptionText:
      "Poule Party is a free location-based mobile game where one player (the Chicken) hides while other players (Hunters) try to find them using a real-time map with a shrinking zone. Separately, we organize occasional paid in-person PouleParty events in Brussels (the \"D-Day\" event series), for which tickets can be purchased on the web at pouleparty.be/registration; the in-app experience itself is and remains free of charge.",
    parties: "Contracting Party",
    partiesText:
      "PouleParty is operated by Julien Rahier, sole trader registered in Belgium, contact julien@rahier.dev. For paid events, you contract directly with Julien Rahier as the event organizer. No company sits between you and us.",
    paidEvents: "Paid Event Tickets",
    paidEventsPrice:
      "Tickets for paid PouleParty events are sold per player at €12 per player, charged as €12 × the team size you select (3, 4, or 5 players) at checkout. Prices are in EUR and include any applicable Belgian VAT. Payment is processed by Stripe (see Privacy Policy for details); we never receive your card data.",
    paidEventsWhatsIncluded:
      "Each ticket includes: one entry to the dated event (e.g. PouleParty D-Day on Saturday, June 6, 2026 from 8:30 PM in Brussels/Ixelles), one welcome drink at the starting bar, and a wristband collected at the final location. Food, additional drinks, transportation and any incidental expenses are not included.",
    paidEventsWithdrawal:
      "Because PouleParty events are leisure activities scheduled for a specific date, the 14-day right of withdrawal granted by Article 9 of the EU Consumer Rights Directive (2011/83/EU) does NOT apply — this exemption is provided by Article 16(l) of that Directive. By completing your purchase, you explicitly acknowledge this and waive the cooling-off period. Tickets are non-refundable except in the cases listed below.",
    paidEventsRefund:
      "Refund rules: (a) if WE cancel or postpone the event for any reason (including weather, force majeure, insufficient registrations, or operator decision), you receive a full refund of the ticket price or, at your option, a transfer to the rescheduled date; (b) if YOU cannot attend, the ticket is non-refundable — but team rosters are freely interchangeable, so you may transfer your spot to another participant up until the day of the event by emailing julien@rahier.dev with the substitution details; (c) if a participant is denied entry to the venue for reasons attributable to them (intoxication, refusal to follow safety instructions, age below 18), no refund is issued.",
    paidEventsForceMajeure:
      "In case of force majeure (e.g. pandemic public-health order, severe weather warning, terror threat, venue closure beyond our control), we will either reschedule the event to a future date — with all tickets transferring automatically — or, if rescheduling is not feasible within 6 months, refund all ticket holders in full.",
    userConduct: "User Conduct",
    conduct1: "Use the app only for its intended purpose (playing the game).",
    conduct2: "Do not attempt to cheat, hack, or reverse-engineer the app.",
    conduct3: "Respect other players and play in safe, public locations.",
    conduct4: "You are responsible for your own safety while playing.",
    location: "Location Data",
    locationText:
      "The app requires location access to function. Your location is shared with other players in your game session only during active gameplay. See our Privacy Policy for details.",
    ugc: "User-Generated Content & Moderation",
    ugcText:
      "Nicknames you enter are visible to other players in your games and in post-game leaderboards. You agree not to use offensive, insulting, harassing, defamatory, sexual, or impersonating nicknames. The app includes an automated profanity filter, but you can also report any player from the leaderboard using the flag button. We review every report and may remove offending content, rename accounts, or ban repeat offenders. Abusive or bad-faith reporters may also be banned.",
    safety: "Physical Safety",
    safetyText:
      "Poule Party is a physical game played in the real world. Watch for traffic, respect private property, and never play in a way that endangers you, other players, or anyone around you. Do not play while driving or cycling. Always follow local laws. You play at your own risk.",
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
    governingLaw: "Governing Law and Jurisdiction",
    governingLawText:
      "These terms are governed by Belgian law. Any dispute arising from these terms or from your participation in a paid PouleParty event will be brought exclusively before the courts of the judicial district of Brussels, Belgium, without prejudice to any non-waivable consumer-protection rights you hold under the law of your habitual residence.",
    odr: "Online Dispute Resolution",
    odrText:
      "As a consumer in the European Union, you may also use the European Commission's Online Dispute Resolution platform to attempt to resolve disputes out of court. The ODR platform is accessible at:",
    odrUrl: "https://ec.europa.eu/consumers/odr",
    contact: "Contact",
    contactText: "If you have questions about these terms, please contact us at",
  },
  createParty: {
    title: "Want to create a party?",
    body: "PouleParty is in beta. To organize or create a party, contact Julien — we'll set it up together.",
    contactEmailLabel: "Email:",
    contactWhatsAppLabel: "WhatsApp:",
  },
  footer: {
    rights: "All rights reserved.",
  },
  inscription: {
    intro: {
      eyebrow: "Real-life GPS hide-and-seek",
      title: "Join\nthe hunt!",
      body: [
        "A Chicken in costume hides somewhere in Ixelles.",
        "Your team has to track them down.",
        "The zone shrinks. The pressure rises. Dirty tricks allowed. 😈",
      ],
      priceLine: "€12 / player · Teams of 3 to 5 · Sat. June 6 · 8:30 PM",
      cta: "SIGN UP →",
    },
    form: {
      title: "Your team",
      subtitle: "Captain's contact info.",
      playerNameLabel: "First & Last name",
      playerNamePlaceholder: "Jane Smith",
      teamNameLabel: "Team name",
      teamNamePlaceholder: "The Hunters",
      emailLabel: "Email",
      emailPlaceholder: "jane@mail.com",
      phoneLabel: "Phone",
      phonePlaceholder: "+32 470 ...",
      teamSizeLabel: "Team size",
      teamSizeUnit: "PLAYERS",
      back: "← BACK",
      next: "RECAP →",
    },
    recap: {
      title: "All good?",
      subtitle: "Check before paying.",
      captain: "Captain",
      team: "Team",
      email: "Email",
      phone: "Phone",
      players: "Players",
      total: "TOTAL",
      note: "€12 / player · 1 free drink at the starting bar · wristband at the final spot",
      paymentSecure: "Secure payment via Stripe (Card · Apple Pay · Google Pay)",
      // XPLAT-H5 (store-audit 2026-05-18, updated 2026-05-18 PM):
      // implicit consent at submit. Disclosure rendered right above
      // the "PAY {n} €" button so the user sees the Terms + Privacy
      // links + the Art. 16(l) waiver at the moment of click.
      consentPrefix: "By clicking Pay, you accept the",
      consentTermsLink: "Terms of Use",
      consentJoin: " and the ",
      consentPrivacyLink: "Privacy Policy",
      consentSuffix: ". Dated leisure event — the 14-day right of withdrawal does not apply (CRD Art. 16(l)).",
      back: "← EDIT",
      // Template: replace {total} with the number — e.g. "PAY 36 € 🔒".
      payButtonTemplate: "PAY {total} € 🔒",
      redirecting: "REDIRECTING…",
      defaultError: "Couldn't start the payment. Try again or write to julien@rahier.dev.",
    },
    fatalError: {
      title: "Invalid registration link",
      body: "This link doesn't carry an event reference. Go back to the original message or contact us at julien@rahier.dev.",
    },
    success: {
      title: "Registration\nconfirmed!",
      line1: "Can't wait to see you at Poule Party #1!",
      line2: "You'll receive an email with your validation code and all the day-of details.",
      spamHint: "No email after a few minutes? Check your spam or write to",
      cluck: "CLUCK CLUCK 🐔",
    },
    cancel: {
      title: "Payment\ncancelled",
      body: "You weren't charged. Feel free to retry whenever.",
      retryButton: "RESUME REGISTRATION",
    },
  },
  deleteAccount: {
    title: "Delete your Poule Party account",
    intro:
      "You can delete your Poule Party account and all the data tied to it. This page is the web-accessible deletion endpoint required by Google Play — the same action is also available inside the app under Settings → Delete Account.",
    dataDeletedTitle: "What is deleted immediately",
    dataDeleted: [
      "Your anonymous Firebase Auth user account",
      "Your /users/{uid} profile document (nickname + push notification token)",
    ],
    dataKeptTitle: "What is kept",
    dataKept: [
      "Past games you participated in keep the team name you used for them (visible only to participants of the same session and the game creator). This is required for game-history integrity.",
      "Anonymous analytics and crash reports (aggregated, not tied to your identity).",
      "Paid event registrations are retained for 12 months after the event for accounting, dispute resolution and tax obligations (see Privacy Policy).",
    ],
    howTitle: "Want a full manual scrub?",
    howText:
      "If you want the team names attached to your past games replaced with a generic label, email us. Include:",
    howList: [
      "Your nickname or team name (as it appeared in the app)",
      "Approximate date of your last game, if you remember it",
      "Subject line: \"Delete my Poule Party account\"",
    ],
    timeframe:
      "We process manual scrub requests within 30 days (usually within a week). You'll receive an email confirmation once it's done.",
    emailButton: "Email a manual scrub request",
    backHome: "Back to home",
    formTitle: "Request a scrub",
    formSubtitle: "Fill this in and we'll process it within 30 days.",
    formEmailLabel: "Your email",
    formEmailPlaceholder: "jane@mail.com",
    formNicknameLabel: "Nickname or team name (optional)",
    formNicknamePlaceholder: "As it appeared in the app",
    formReasonLabel: "Anything else? (optional)",
    formReasonPlaceholder: "Approximate date of last game, etc.",
    formSubmit: "SUBMIT REQUEST",
    formSubmitting: "SUBMITTING…",
    formSuccess:
      "Got it — we received your request and will process it within 30 days. A copy was emailed to {email}.",
    formErrorGeneric:
      "Something went wrong. Try again or email us at julien@rahier.dev.",
    formErrorInvalidEmail: "Please enter a valid email address.",
    fallbackHint: "Form not working? You can also email us directly:",
  },
};
