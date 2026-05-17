export default {
  nav: {
    privacy: "Confidentialité",
    terms: "CGU",
    support: "Support",
  },
  home: {
    tagline:
      "Un cache-cache GPS grandeur nature ! Un joueur est la Poule, les autres sont Chasseurs. La zone rétrécit, la traque s'intensifie !",
    appComingSoon: "Application bientôt disponible...",
    downloadApp: "Télécharge l'application !",
    appStore: "App Store",
    googlePlay: "Google Play",
    androidDisclaimer: "⚠️ Android : l'app est en test fermé. Ton adresse email doit être enregistrée côté développeur pour pouvoir la télécharger. Si ça ne marche pas, préviens-nous sur le groupe WhatsApp !",
  },
  privacy: {
    title: "Politique de confidentialité",
    lastUpdated: "Dernière mise à jour : 12 mai 2026",
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
    lastUpdated: "Dernière mise à jour : 12 mai 2026",
    acceptance: "Acceptation des conditions",
    acceptanceText:
      "En téléchargeant, installant ou utilisant Poule Party, vous acceptez ces conditions d'utilisation. Si vous n'êtes pas d'accord, veuillez ne pas utiliser l'application.",
    description: "Description du service",
    descriptionText:
      "Poule Party est un jeu mobile gratuit basé sur la géolocalisation dans lequel un joueur (la Poule) se cache tandis que les autres joueurs (les Chasseurs) tentent de la trouver à l'aide d'une carte en temps réel avec une zone qui rétrécit.",
    userConduct: "Comportement des utilisateurs",
    conduct1: "Utilisez l'application uniquement dans le cadre prévu (jouer au jeu).",
    conduct2: "Ne tentez pas de tricher, pirater ou rétro-ingéniérer l'application.",
    conduct3: "Respectez les autres joueurs et jouez dans des lieux publics et sûrs.",
    conduct4: "Vous êtes responsable de votre propre sécurité pendant le jeu.",
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
  createParty: {
    title: "Envie de créer une partie ?",
    body: "PouleParty est en version beta. Pour organiser ou créer une partie, contacte Julien — on met ça en place ensemble.",
    contactEmailLabel: "Email :",
    contactWhatsAppLabel: "WhatsApp :",
  },
  footer: {
    rights: "Tous droits réservés.",
  },
  inscription: {
    intro: {
      eyebrow: "Cache-cache GPS grandeur nature",
      title: "Rejoins\nla chasse !",
      body: [
        "Une Poule déguisée se planque dans Ixelles.",
        "Toi et ton équipe, vous la traquez.",
        "La zone rétrécit. La tension monte. Les coups bas sont autorisés. 😈",
      ],
      priceLine: "12 € / pers · Équipes de 3 à 5 · Sam. 6 juin · 20h30",
      cta: "S'INSCRIRE →",
    },
    form: {
      title: "Ton équipe",
      subtitle: "Infos du capitaine de l'équipe.",
      playerNameLabel: "Prénom & Nom",
      playerNamePlaceholder: "Jean Dupont",
      teamNameLabel: "Nom d'équipe",
      teamNamePlaceholder: "Les Chasseurs",
      emailLabel: "Adresse mail",
      emailPlaceholder: "jean@mail.com",
      phoneLabel: "Téléphone",
      phonePlaceholder: "+32 470 ...",
      teamSizeLabel: "Taille de l'équipe",
      teamSizeUnit: "JOUEURS",
      back: "← RETOUR",
      next: "RÉCAP →",
    },
    recap: {
      title: "Tout bon ?",
      subtitle: "Vérifie avant de payer.",
      captain: "Capitaine",
      team: "Équipe",
      email: "Email",
      phone: "Téléphone",
      players: "Joueurs",
      total: "TOTAL",
      note: "12 € / pers · 1 verre offert au bar de départ · bracelet lieu final",
      paymentSecure: "Paiement sécurisé via Stripe (CB · Apple Pay · Google Pay)",
      back: "← MODIFIER",
      payButtonTemplate: "PAYER {total} € 🔒",
      redirecting: "REDIRECTION…",
      defaultError: "Impossible de démarrer le paiement. Réessaie ou contacte julien@rahier.dev.",
    },
    fatalError: {
      title: "Lien d'inscription invalide",
      body: "Le lien que tu as suivi ne contient pas de référence d'événement. Reviens à la communication d'origine ou contacte-nous à julien@rahier.dev.",
    },
    success: {
      title: "Inscription\nconfirmée !",
      line1: "On a hâte de te voir à la Poule Party #1 !",
      line2: "Tu vas recevoir un email avec ton code de validation et toutes les infos pour le jour J.",
      spamHint: "Pas d'email d'ici quelques minutes ? Vérifie tes spams ou écris à",
      cluck: "COT COT 🐔",
    },
    cancel: {
      title: "Paiement\nannulé",
      body: "Tu n'as pas été débité. Tu peux réessayer quand tu veux.",
      retryButton: "REPRENDRE L'INSCRIPTION",
    },
  },
  deleteAccount: {
    title: "Supprimer ton compte Poule Party",
    intro:
      "Tu peux supprimer ton compte Poule Party et toutes les données associées. Cette page est le point de suppression web exigé par Google Play — la même action est aussi disponible dans l'app via Paramètres → Supprimer mes données.",
    dataDeletedTitle: "Ce qui est supprimé",
    dataDeleted: [
      "Ton identifiant Firebase anonyme",
      "Ton pseudo et ton token de notifications push",
      "Toutes les données de profil stockées sous /users/{ton-id}",
    ],
    dataKeptTitle: "Ce qui est conservé",
    dataKept: [
      "Les parties créées ou rejointes sont conservées pour que les autres participants voient encore les résultats. Ton pseudo est remplacé par un libellé générique.",
      "Les analytics et rapports de crash anonymes (agrégés, non liés à ton identité).",
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
