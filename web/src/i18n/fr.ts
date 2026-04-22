export default {
  nav: {
    register: "Inscription",
    privacy: "Confidentialité",
    terms: "CGU",
    support: "Support",
  },
  home: {
    tagline:
      "Un cache-cache GPS grandeur nature ! Un joueur est la Poule, les autres sont Chasseurs. La zone rétrécit, la traque s'intensifie !",
    cta: "Prochain event : 23 avril !",
    appComingSoon: "Application bientôt disponible...",
    downloadApp: "Télécharge l'application !",
    appStore: "App Store",
    googlePlay: "Google Play",
    androidDisclaimer: "⚠️ Android : l'app est en test fermé. Ton adresse email doit être enregistrée côté développeur pour pouvoir la télécharger. Si ça ne marche pas, préviens-nous sur le groupe WhatsApp !",
  },
  privacy: {
    title: "Politique de confidentialité",
    lastUpdated: "Dernière mise à jour : 4 mars 2026",
    overview: "Responsable du traitement",
    overviewText:
      "Poule Party (\"nous\", \"notre\", \"l'application\") est un jeu mobile de géolocalisation. Le responsable du traitement de vos données personnelles est :",
    controllerDetails: "Julien Rahier, julien@rahier.dev, Belgique",
    dataCollected: "Données collectées",
    locationData: "Données de localisation :",
    locationDataText:
      "Vos coordonnées GPS sont collectées pendant une partie et partagées avec les autres joueurs de votre session. Le suivi de localisation s'arrête à la fin de la partie.",
    auth: "Authentification anonyme :",
    authText:
      "Nous utilisons Firebase Anonymous Authentication pour générer un identifiant anonyme pour votre appareil pendant le jeu. Aucun email, nom ou compte personnel n'est requis.",
    playerName: "Nom du joueur :",
    playerNameText:
      "Le nom d'affichage que vous choisissez en rejoignant une partie. Il est stocké localement sur votre appareil et partagé avec les autres joueurs pendant une session de jeu. Il peut également être enregistré dans les résultats de la partie (ex. gagnants). Il n'est lié à aucune identité personnelle.",
    analytics: "Analytiques :",
    analyticsText:
      "Nous utilisons Firebase Analytics pour collecter des données d'utilisation anonymes (ouvertures de l'app, vues d'écran) et Firebase Crashlytics pour les rapports de crash, afin d'améliorer l'application. Aucune information personnelle identifiable n'est collectée via ces services.",
    legalBasis: "Base légale du traitement",
    legalBasisIntro: "Conformément au RGPD (Art. 6), nous traitons vos données sur les bases légales suivantes :",
    legalBasisConsent: "Consentement (Art. 6(1)(a)) :",
    legalBasisConsentText: "Données de localisation : vous accordez explicitement la permission de localisation avant toute collecte de données. Vous pouvez retirer votre consentement à tout moment en révoquant les permissions de localisation dans les paramètres de votre appareil.",
    legalBasisLegitimate: "Intérêt légitime (Art. 6(1)(f)) :",
    legalBasisLegitimateText: "Authentification anonyme et rapports de crash : nécessaires au fonctionnement de l'application et au maintien de la qualité du service. Notre intérêt légitime ne prévaut pas sur vos droits, car les données sont entièrement anonymes.",
    howWeUse: "Utilisation de vos données",
    howWeUse1:
      "Les données de localisation sont utilisées uniquement pour les mécaniques de jeu en temps réel (affichage des positions sur la carte, détection de zone).",
    howWeUse2:
      "Les données de localisation sont stockées dans Firebase Firestore pendant la partie. Les données de jeu (y compris l'historique de localisation) peuvent être conservées après la fin de la partie à des fins opérationnelles et peuvent être supprimées sur demande.",
    howWeUse3:
      "Les données analytiques sont utilisées pour comprendre les habitudes d'utilisation et corriger les bugs.",
    thirdParties: "Services tiers",
    thirdPartiesIntro: "Nous utilisons les services tiers suivants pour faire fonctionner l'application. Chacun dispose de sa propre politique de confidentialité :",
    thirdPartyFirebaseAnalytics: "Google Analytics for Firebase",
    thirdPartyFirebaseAnalyticsUrl: "https://firebase.google.com/support/privacy",
    thirdPartyCrashlytics: "Firebase Crashlytics",
    thirdPartyCrashlyticsUrl: "https://firebase.google.com/support/privacy/",
    thirdPartyMapbox: "Mapbox",
    thirdPartyMapboxUrl: "https://www.mapbox.com/legal/privacy",
    thirdPartyStripe: "Stripe",
    thirdPartyStripeUrl: "https://stripe.com/privacy",
    thirdPartyStripeText: "pour les paiements des parties payantes (forfait créateur, caution joueur). Nous n'avons jamais accès à vos données bancaires : elles sont transmises directement à Stripe qui les traite en tant que responsable de traitement indépendant.",
    dataSharing: "Partage des données",
    dataSharingText:
      "Nous ne vendons, n'échangeons ni ne partageons vos données personnelles avec des tiers à des fins commerciales. Les données de localisation sont uniquement partagées avec les autres joueurs de la même session pendant le jeu.",
    internationalTransfers: "Transferts internationaux de données",
    internationalTransfersText:
      "Vos données sont traitées par les services Google Firebase, dont les serveurs peuvent être situés en dehors de l'Espace économique européen (EEE), notamment aux États-Unis. Google opère dans le cadre du EU-US Data Privacy Framework et des Clauses contractuelles types (CCT) pour garantir un niveau de protection adéquat tel qu'exigé par le RGPD. Mapbox traite également les données cartographiques sous des garanties similaires.",
    dataRetention: "Conservation des données",
    dataRetentionText:
      "Les données de jeu (y compris l'historique de localisation) peuvent être conservées dans Firebase Firestore après la fin d'une partie. Aucun suivi de localisation à long terme n'a lieu en dehors des parties actives. Vous pouvez demander la suppression de vos données de jeu en nous contactant à julien@rahier.dev. Les jetons d'authentification anonyme peuvent persister sur votre appareil mais peuvent être supprimés en supprimant votre compte dans les paramètres de l'application ou en désinstallant l'application.",
    children: "Protection des mineurs",
    childrenText:
      "L'application ne s'adresse pas aux enfants de moins de 16 ans. Nous ne collectons pas sciemment d'informations personnelles auprès d'enfants de moins de 16 ans. Si vous pensez qu'un enfant de moins de 16 ans nous a fourni des données personnelles, contactez-nous afin que nous puissions les supprimer.",
    rights: "Vos droits au titre du RGPD",
    rightsIntro: "En tant qu'utilisateur dans l'Espace économique européen, vous disposez des droits suivants :",
    rightAccess: "Droit d'accès (Art. 15) :",
    rightAccessText: "Vous pouvez demander une copie des données personnelles que nous détenons à votre sujet.",
    rightRectification: "Droit de rectification (Art. 16) :",
    rightRectificationText: "Vous pouvez demander la correction de données inexactes.",
    rightErasure: "Droit à l'effacement (Art. 17) :",
    rightErasureText: "Vous pouvez demander la suppression de vos données. Vous pouvez supprimer votre compte d'authentification anonyme directement dans l'application via Paramètres > Supprimer mes données. Pour la suppression complète des données de jeu stockées dans Firestore, veuillez nous contacter à julien@rahier.dev.",
    rightRestriction: "Droit à la limitation (Art. 18) :",
    rightRestrictionText: "Vous pouvez demander que nous limitions le traitement de vos données.",
    rightPortability: "Droit à la portabilité (Art. 20) :",
    rightPortabilityText: "Vous pouvez demander vos données dans un format structuré et lisible par machine.",
    rightObject: "Droit d'opposition (Art. 21) :",
    rightObjectText: "Vous pouvez vous opposer au traitement de vos données fondé sur l'intérêt légitime.",
    rightWithdraw: "Droit de retrait du consentement :",
    rightWithdrawText: "Vous pouvez retirer votre consentement au suivi de localisation à tout moment en révoquant les permissions de localisation dans les paramètres de votre appareil.",
    rightsExercise: "Pour exercer l'un de ces droits, contactez-nous à julien@rahier.dev. Nous répondrons dans un délai de 30 jours.",
    supervisory: "Autorité de contrôle",
    supervisoryText:
      "Si vous estimez que vos droits en matière de protection des données ont été violés, vous avez le droit de déposer une plainte auprès de votre autorité de contrôle locale. En Belgique, il s'agit de :",
    supervisoryAuthority: "Autorité de protection des données (APD)",
    supervisoryUrl: "https://www.autoriteprotectiondonnees.be",
    contact: "Contact",
    contactText: "Si vous avez des questions concernant cette politique de confidentialité, contactez-nous à",
  },
  support: {
    title: "Support",
    needHelp: "Besoin d'aide ?",
    needHelpText:
      "Si vous rencontrez des problèmes avec Poule Party ou avez des questions, nous sommes là pour vous aider.",
    commonIssues: "Problèmes courants",
    locationTitle: "La localisation ne fonctionne pas",
    locationText:
      "Assurez-vous d'avoir accordé les permissions de localisation à Poule Party dans les paramètres de votre appareil. L'application nécessite un accès \"En cours d'utilisation\" ou \"Toujours\" pour fonctionner pendant le jeu.",
    joinTitle: "Impossible de rejoindre une partie",
    joinText:
      "Vérifiez que le code de partie est correct (6 caractères). La partie a peut-être déjà commencé ou est terminée. Demandez un nouveau code au créateur de la partie.",
    mapTitle: "La carte ne se charge pas",
    mapText:
      "Assurez-vous d'avoir une connexion internet stable. Essayez de fermer et rouvrir l'application.",
    contactTitle: "Nous contacter",
    contactText:
      "Pour signaler des bugs, demander des fonctionnalités ou toute autre question, contactez-nous à",
    contactFooter:
      "Merci d'inclure le modèle de votre appareil, la version de l'OS et une description du problème pour que nous puissions vous aider plus rapidement.",
  },
  terms: {
    title: "Conditions d'utilisation",
    lastUpdated: "Dernière mise à jour : 22 avril 2026",
    acceptance: "Acceptation des conditions",
    acceptanceText:
      "En téléchargeant, installant ou utilisant Poule Party, vous acceptez ces conditions d'utilisation. Si vous n'êtes pas d'accord, veuillez ne pas utiliser l'application.",
    description: "Description du service",
    descriptionText:
      "Poule Party est un jeu mobile basé sur la géolocalisation dans lequel un joueur (la Poule) se cache tandis que les autres joueurs (les Chasseurs) tentent de la trouver à l'aide d'une carte en temps réel avec une zone qui rétrécit. L'application est gratuite ; certaines parties peuvent être organisées avec un forfait créateur ou une caution remboursable (voir Tarification ci-dessous).",
    userConduct: "Comportement des utilisateurs",
    conduct1: "Utilisez l'application uniquement dans le cadre prévu (jouer au jeu).",
    conduct2: "Ne tentez pas de tricher, pirater ou rétro-ingéniérer l'application.",
    conduct3: "Respectez les autres joueurs et jouez dans des lieux publics et sûrs.",
    conduct4: "Vous êtes responsable de votre propre sécurité pendant le jeu.",
    pricing: "Tarification & paiements",
    pricingIntro:
      "Poule Party propose trois modes de tarification, choisis par le créateur de la partie :",
    pricingFree: "Parties gratuites :",
    pricingFreeText:
      "Aucun paiement n'est requis. Créateur et Chasseurs jouent sans frais.",
    pricingFlat: "Forfait créateur :",
    pricingFlatText:
      "Le créateur paie un montant fixe à la création pour le service d'organisation et d'hébergement d'un événement réel (minuterie, gestion de zone, notifications, support). Il s'agit d'un forfait de service pour une activité physique et il n'est pas remboursable une fois la partie lancée. Un code promo peut être appliqué si vous en avez reçu un.",
    pricingDeposit: "Caution remboursable :",
    pricingDepositText:
      "Chaque Chasseur paie une caution à l'inscription. La caution est conservée par notre prestataire de paiement (Stripe) et est remboursable à chaque participant après la partie, quel que soit le résultat. La caution n'est pas une mise, pas un pot de jeu, et n'est jamais redistribuée aux gagnants. Poule Party ne prend aucune commission sur la caution.",
    pricingNoGambling: "Pas de jeu d'argent, pas de gain :",
    pricingNoGamblingText:
      "Poule Party est un jeu physique basé sur l'habileté. Gagner une partie ne donne droit à aucun gain en argent ni bien numérique. Nous n'organisons pas de jeu de hasard et ne distribuons aucun prix en espèces.",
    pricingPayments:
      "Tous les paiements sont traités par Stripe. Nous ne voyons jamais et ne stockons jamais vos coordonnées bancaires. Pour demander le remboursement de ta caution après la partie, envoie un email à julien@rahier.dev en précisant le code de la partie et l'adresse email utilisée à l'inscription — nous effectuons le remboursement via Stripe sur le moyen de paiement d'origine en quelques jours ouvrés.",
    location: "Données de localisation",
    locationText:
      "L'application nécessite l'accès à la localisation pour fonctionner. Votre position est partagée avec les autres joueurs de votre session uniquement pendant le jeu. Consultez notre politique de confidentialité pour plus de détails.",
    ugc: "Contenu utilisateur & modération",
    ugcText:
      "Les pseudos que vous saisissez sont visibles par les autres joueurs de vos parties et dans les classements. Vous acceptez de ne pas utiliser de pseudos offensants, injurieux, harcelants, diffamatoires, sexuels ou usurpant l'identité d'autrui. L'app inclut un filtre anti-insultes automatique, mais vous pouvez aussi signaler un joueur depuis le classement via le bouton drapeau. Nous examinons chaque signalement et pouvons retirer un contenu, renommer un compte ou bannir les récidivistes. Les signalements abusifs ou de mauvaise foi peuvent aussi entraîner un bannissement.",
    safety: "Sécurité physique",
    safetyText:
      "Poule Party est un jeu physique joué dans le monde réel. Fais attention à la circulation, respecte la propriété privée et ne joue jamais d'une manière qui mette en danger toi, les autres joueurs ou les personnes autour. Ne joue pas en conduisant ni à vélo. Respecte les lois locales. Tu joues à tes propres risques.",
    disclaimer: "Avertissement",
    disclaimerText:
      "L'application est fournie \"en l'état\" sans garantie d'aucune sorte. Nous ne garantissons pas un service ininterrompu ou sans erreur.",
    liability: "Limitation de responsabilité",
    liabilityText:
      "Julien Rahier ne pourra être tenu responsable de tout dommage résultant de l'utilisation de l'application, y compris mais sans s'y limiter, les blessures corporelles, les dommages matériels ou la perte de données.",
    termination: "Résiliation",
    terminationText:
      "Nous nous réservons le droit de suspendre ou de résilier l'accès à l'application à tout moment, sans préavis, pour tout comportement violant ces conditions.",
    changes: "Modifications des conditions",
    changesText:
      "Nous pouvons mettre à jour ces conditions de temps à autre. L'utilisation continue de l'application après les modifications constitue une acceptation des nouvelles conditions.",
    contact: "Contact",
    contactText: "Si vous avez des questions concernant ces conditions, contactez-nous à",
  },
  register: {
    date: "23 avril 2026 à 19h00",
    intro:
      "Prépare-toi pour une expérience totalement insolite : une chasse à la poule grandeur nature, en plein cœur de la ville.",
    description:
      "Inspirée du concept de jeu urbain façon \"cache-cache + jeu de piste\", la Poule Party est une aventure collective où stratégie, rapidité et créativité feront toute la différence.",
    conceptTitle: "🎯 Le concept",
    conceptText:
      "Une \"Poule\" est lâchée dans la ville avec quelques minutes d'avance…",
    mission: "Ta mission ? La retrouver avant les autres équipes.",
    zoneText:
      "Grâce à un système de localisation évolutif, une zone autour de la Poule apparaît et se réduit progressivement… À toi de te rapprocher, d'enquêter et de foncer au bon moment.",
    butWait: "Mais ce n'est pas tout 👇",
    pointsTitle: "🏆 Gagne des points (et la gloire)",
    pointsList: [
      "🧭 Retrouve la Poule avant les autres",
      "📸 Réalise des défis fun et décalés",
      "🎭 Fais preuve de créativité (et d'audace)",
      "😈 (Optionnel) Sabote les autres équipes",
    ],
    pointsOutro:
      "Chaque action te rapporte des points… et à la fin, une seule équipe sera sacrée gagnante.",
    whyTitle: "🎉 Pourquoi participer ?",
    whyList: [
      "Une activité originale et ultra fun",
      "Parfait pour rencontrer du monde",
      "Accessible à tous (pas besoin d'être sportif)",
      "Des souvenirs mémorables (et souvent très drôles)",
    ],
    infoTitle: "📅 Infos pratiques",
    infoList: [
      "📆 Date : 23 avril à 19h",
      "👥 Places limitées : 35 participants",
      "🎮 Format : équipes + jeu en extérieur",
      "⏱ Durée : ± 2h de jeu",
      "📍 Lieu de départ : communiqué après inscription",
    ],
    spotsLeft: "{0}/{1} inscrits",
    formTitle: "Inscription",
    firstNameLabel: "Prénom",
    firstNamePlaceholder: "Jean",
    lastNameLabel: "Nom",
    lastNamePlaceholder: "Dupont",
    emailLabel: "Email",
    emailHint: "📱 Sur Android, mets ton adresse Play Store : c'est celle-là qui te donnera accès à l'app (test fermé)",
    emailPlaceholder: "jean@example.com",
    gsmLabel: "GSM",
    gsmHint: "Pour t'ajouter au groupe WhatsApp de l'event",
    gsmPlaceholder: "+32 470 12 34 56",
    emailError: "Adresse email invalide",
    gsmTooShort: "Il manque {0} chiffre(s)",
    gsmTooLong: "Il y a {0} chiffre(s) en trop",
    gsmFormatError: "Le numéro doit commencer par + ou 0",
    willingToPayLabel: "Combien serais-tu prêt à payer pour participer ?",
    willingToPayPlaceholder: "Ex : 5€, 10€, gratuit…",
    commentLabel: "Un commentaire ? (optionnel)",
    commentHint: "Idées, suggestions, recommandations…",
    commentPlaceholder: "Ex : idée de power-up, question sur l'event…",
    submitButton: "Je m'inscris !",
    submitting: "Inscription en cours…",
    duplicateText: "Cet email est déjà inscrit ! Si c'est une erreur, contacte-nous à julien@rahier.dev.",
    fullText: "Désolé, toutes les places sont prises !",
    errorText: "Une erreur est survenue. Réessaye !",
    successTitle: "Inscription confirmée !",
    successText:
      "Tu recevras un email avec tous les détails avant l'événement. Prépare-toi pour la chasse !",
  },
  footer: {
    rights: "Tous droits réservés.",
  },
  deleteAccount: {
    title: "Supprimer ton compte Poule Party",
    intro:
      "Tu peux supprimer ton compte Poule Party et toutes les données associées. Cette page est le point de suppression web exigé par Google Play — la même action est aussi disponible dans l'app via Paramètres → Supprimer mes données.",
    dataDeletedTitle: "Ce qui est supprimé",
    dataDeleted: [
      "Ton identifiant Firebase anonyme",
      "Ton pseudo, ta référence client Stripe et ton token de notifications push",
      "Toutes les données de profil stockées sous /users/{ton-id}",
    ],
    dataKeptTitle: "Ce qui est conservé",
    dataKept: [
      "Les parties créées ou rejointes sont conservées pour que les autres participants voient encore les résultats. Ton pseudo est remplacé par un libellé générique.",
      "Les analytics et rapports de crash anonymes (agrégés, non liés à ton identité).",
      "Les enregistrements de paiement que Stripe et notre comptabilité doivent légalement conserver (en général 7–10 ans en Belgique).",
    ],
    howTitle: "Comment demander la suppression",
    howText:
      "Envoie un email à l'adresse ci-dessous depuis l'adresse où tu veux recevoir la confirmation, en incluant :",
    howList: [
      "Ton pseudo (tel qu'il apparaît dans l'app)",
      "La date approximative de ta dernière partie, si tu t'en souviens",
      "Objet : « Suppression de mon compte Poule Party »",
    ],
    timeframe:
      "Nous traitons les demandes de suppression sous 30 jours (en général sous une semaine). Tu recevras un email de confirmation dès que c'est fait.",
    emailButton: "Envoyer la demande par email",
    backHome: "Retour à l'accueil",
  },
};
