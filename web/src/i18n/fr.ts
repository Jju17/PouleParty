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
    dDayEyebrow: "PouleParty D-Day",
    dDayTitle: "🐔 Samedi 6 juin",
    dDayBody: "Une Poule se cache dans Ixelles. Ton équipe doit la traquer. 12 € / joueur · Équipes de 3 à 5.",
    dDayCta: "INSCRIS TON ÉQUIPE →",
  },
  privacy: {
    title: "Politique de confidentialité",
    lastUpdated: "Dernière mise à jour : 18 mai 2026",
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
    paidEventData: "Inscription à un événement payant :",
    paidEventDataText:
      "Lorsque tu t'inscris à un événement PouleParty payant via le formulaire web (pouleparty.be/inscription), nous collectons : le nom complet du capitaine, le nom d'équipe, l'adresse email, le numéro de téléphone, la taille de l'équipe, ton adresse IP et ta langue, l'identifiant de session Stripe Checkout, ainsi qu'un code de validation à 6 caractères que nous générons pour l'événement. Ces données sont stockées dans notre collection Firestore `/eventRegistrations` et transmises aux services tiers listés ci-dessous pour le paiement, l'envoi de l'email de confirmation et la logistique sur place. Les données de carte bancaire ne sont jamais collectées par nous — elles sont saisies directement sur la page Checkout hébergée par Stripe.",
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
    thirdPartyStripe: "Stripe (traitement des paiements pour les événements payants — Stripe Technology Europe Ltd en Irlande, avec transfert ultérieur vers Stripe Inc. aux États-Unis sous Clauses contractuelles types)",
    thirdPartyStripeUrl: "https://stripe.com/privacy",
    thirdPartyResend: "Resend (emails de confirmation transactionnels pour les événements payants — Resend Inc. aux États-Unis sous Clauses contractuelles types)",
    thirdPartyResendUrl: "https://resend.com/legal/privacy-policy",
    thirdPartyGoogleSheets: "Google Sheets (liste opérationnelle pour le check-in sur place le jour J — Google LLC dans le cadre du EU-US Data Privacy Framework)",
    thirdPartyGoogleSheetsUrl: "https://policies.google.com/privacy",
    dataSharing: "Partage des données",
    dataSharingText:
      "Nous ne vendons, n'échangeons ni ne partageons vos données personnelles avec des tiers à des fins commerciales. Les données de localisation sont uniquement partagées avec les autres joueurs de la même session pendant le jeu.",
    internationalTransfers: "Transferts internationaux de données",
    internationalTransfersText:
      "Tes données sont traitées par les services Google Firebase, dont les serveurs peuvent être situés en dehors de l'Espace économique européen (EEE), notamment aux États-Unis. Google opère dans le cadre du EU-US Data Privacy Framework et des Clauses contractuelles types (CCT) pour garantir un niveau de protection adéquat tel qu'exigé par le RGPD. Mapbox traite les données cartographiques sous des garanties similaires. Pour les inscriptions aux événements payants : Stripe achemine les paiements via Stripe Technology Europe Ltd (Irlande) et transmet des signaux anonymisés de risque/fraude à Stripe Inc. (États-Unis) sous CCT. Resend (États-Unis) transmet l'email de confirmation sous CCT. Les données Google Sheets (liste de l'événement) sont stockées sur l'infrastructure Google LLC (États-Unis) dans le cadre du EU-US Data Privacy Framework.",
    dataRetention: "Conservation des données",
    dataRetentionText:
      "Les données de jeu (y compris l'historique de localisation) peuvent être conservées dans Firebase Firestore après la fin d'une partie. Aucun suivi de localisation à long terme n'a lieu en dehors des parties actives. Les inscriptions aux événements payants stockées dans `/eventRegistrations` sont conservées pendant 12 mois après la date de l'événement pour la comptabilité, la résolution des litiges et les obligations fiscales, puis purgées. La feuille Google Sheets utilisée pour le check-in sur place est purgée dans les 30 jours suivant l'événement. Tu peux demander la suppression de tes données de jeu en nous contactant à julien@rahier.dev. Les jetons d'authentification anonyme peuvent persister sur ton appareil mais peuvent être supprimés en supprimant ton compte dans les paramètres de l'application ou en désinstallant l'application.",
    cookies: "Cookies et technologies similaires",
    cookiesText:
      "Le site web public utilise uniquement des cookies strictement nécessaires : Firebase App Check pour la protection anti-bot du formulaire d'inscription, et les cookies hébergés par Stripe sur la page Checkout Stripe elle-même (gérés par Stripe, voir leur politique de confidentialité). Nous n'utilisons aucun cookie publicitaire, analytique ou de tracking sur les pages web publiques. Aucune bannière cookies n'est affichée car le consentement au titre de la directive ePrivacy n'est pas requis pour les cookies strictement nécessaires.",
    children: "Conditions d'âge",
    childrenText:
      "Le jeu mobile gratuit PouleParty est recommandé à partir de 13 ans ; les utilisateurs de moins de 13 ans doivent faire configurer l'appareil et accorder les permissions de localisation par un parent ou tuteur. L'événement payant PouleParty D-Day organisé dans des lieux publics (bars, ville de Bruxelles) est réservé aux participants âgés de 18 ans et plus en raison des conditions de licence des lieux. Nous ne collectons pas sciemment de données personnelles auprès d'enfants de moins de 13 ans via l'app, ni auprès de toute personne de moins de 18 ans via le formulaire d'inscription à l'événement payant. Si tu penses qu'un mineur nous a fourni des données en dehors de ces limites, contacte-nous à julien@rahier.dev pour que nous puissions les supprimer.",
    rights: "Vos droits au titre du RGPD",
    rightsIntro: "En tant qu'utilisateur dans l'Espace économique européen, vous disposez des droits suivants :",
    rightAccess: "Droit d'accès (Art. 15) :",
    rightAccessText: "Vous pouvez demander une copie des données personnelles que nous détenons à votre sujet.",
    rightRectification: "Droit de rectification (Art. 16) :",
    rightRectificationText: "Vous pouvez demander la correction de données inexactes.",
    rightErasure: "Droit à l'effacement (Art. 17) :",
    rightErasureText: "Tu peux demander la suppression de tes données. Le bouton Paramètres > Supprimer mon compte dans l'app supprime immédiatement ton compte Firebase Auth anonyme et le document de profil `/users/{uid}`. Les parties auxquelles tu as participé (et le nom d'équipe que tu y as utilisé) sont conservées indéfiniment pour l'intégrité de l'historique des parties — elles ne sont visibles que par les participants de la même session et par le créateur de la partie. Pour un effacement complet des données de parties passées (y compris ton nom d'équipe dans les listes de gagnants), contacte-nous à julien@rahier.dev et nous traiterons l'effacement manuel sous 30 jours. Les inscriptions aux événements payants sont supprimées sur demande (sous réserve de la période de conservation comptable de 12 mois mentionnée ci-dessus).",
    rightRestriction: "Droit à la limitation (Art. 18) :",
    rightRestrictionText: "Vous pouvez demander que nous limitions le traitement de vos données.",
    rightPortability: "Droit à la portabilité (Art. 20) :",
    rightPortabilityText: "Tu peux demander tes données dans un format structuré et lisible par machine. Envoie une demande à julien@rahier.dev et nous te répondrons sous 30 jours avec un export JSON contenant ton document de profil `/users/{uid}`, les noms d'équipe et records de victoires liés à ton identifiant utilisateur anonyme à travers les parties passées, ainsi que toute inscription à un événement payant liée à ton adresse email le cas échéant.",
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
    lastUpdated: "Dernière mise à jour : 18 mai 2026",
    acceptance: "Acceptation des conditions",
    acceptanceText:
      "En téléchargeant, installant ou utilisant Poule Party, ou en t'inscrivant à un événement PouleParty payant, tu acceptes ces conditions d'utilisation. Si tu n'es pas d'accord, n'utilise pas l'application et ne t'inscris pas à un événement.",
    description: "Description du service",
    descriptionText:
      "Poule Party est un jeu mobile gratuit basé sur la géolocalisation dans lequel un joueur (la Poule) se cache tandis que les autres joueurs (les Chasseurs) tentent de la trouver à l'aide d'une carte en temps réel avec une zone qui rétrécit. Séparément, nous organisons occasionnellement des événements PouleParty payants en personne à Bruxelles (la série d'événements « D-Day »), dont les billets peuvent être achetés sur le web à l'adresse pouleparty.be/inscription ; l'expérience in-app, elle, reste entièrement gratuite.",
    parties: "Partie contractante",
    partiesText:
      "PouleParty est exploité par Julien Rahier, indépendant en personne physique enregistré en Belgique, contact julien@rahier.dev. Pour les événements payants, tu contractes directement avec Julien Rahier en qualité d'organisateur de l'événement. Aucune société ne s'interpose entre toi et nous.",
    paidEvents: "Billets pour événements payants",
    paidEventsPrice:
      "Les billets pour les événements PouleParty payants sont vendus par joueur au prix de 12 € par joueur, facturés 12 € × la taille d'équipe sélectionnée (3, 4 ou 5 joueurs) au moment du paiement. Les prix sont en EUR et incluent la TVA belge applicable le cas échéant. Le paiement est traité par Stripe (voir la politique de confidentialité pour les détails) ; nous ne recevons jamais tes données de carte bancaire.",
    paidEventsWhatsIncluded:
      "Chaque billet comprend : une entrée pour l'événement à la date indiquée (par exemple PouleParty D-Day le samedi 6 juin 2026 à partir de 20h30 à Bruxelles/Ixelles), un verre de bienvenue au bar de départ, et un bracelet remis au lieu final. La nourriture, les boissons supplémentaires, les transports et toute dépense annexe ne sont pas inclus.",
    paidEventsWithdrawal:
      "Les événements PouleParty étant des activités de loisir programmées à une date précise, le droit de rétractation de 14 jours prévu par l'article 9 de la directive européenne 2011/83/UE relative aux droits des consommateurs NE s'applique PAS — cette exception est prévue par l'article 16(l) de cette même directive. En finalisant ton achat, tu reconnais explicitement cette exception et renonces au délai de rétractation. Les billets ne sont pas remboursables sauf dans les cas listés ci-dessous.",
    paidEventsRefund:
      "Règles de remboursement : (a) si NOUS annulons ou reportons l'événement pour quelque raison que ce soit (météo, force majeure, inscriptions insuffisantes, décision de l'organisateur), tu reçois un remboursement intégral du prix du billet ou, à ton choix, un transfert vers la date reportée ; (b) si TU ne peux pas venir, le billet n'est pas remboursable — mais les compositions d'équipe sont librement interchangeables, donc tu peux céder ta place à un autre participant jusqu'au jour de l'événement en envoyant un email à julien@rahier.dev avec les détails de la substitution ; (c) si un participant se voit refuser l'accès au lieu pour des raisons qui lui sont imputables (ivresse, refus de suivre les consignes de sécurité, âge inférieur à 18 ans), aucun remboursement n'est accordé.",
    paidEventsForceMajeure:
      "En cas de force majeure (par exemple décret sanitaire de pandémie, alerte météo grave, menace terroriste, fermeture du lieu indépendante de notre volonté), nous reportons l'événement à une date ultérieure — tous les billets étant automatiquement transférés — ou, si le report n'est pas possible dans un délai de 6 mois, nous remboursons intégralement tous les détenteurs de billets.",
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
    governingLaw: "Droit applicable et juridiction",
    governingLawText:
      "Les présentes conditions sont régies par le droit belge. Tout litige né des présentes conditions ou de ta participation à un événement PouleParty payant sera porté exclusivement devant les juridictions de l'arrondissement judiciaire de Bruxelles, Belgique, sans préjudice des droits de protection du consommateur auxquels il ne peut être renoncé dont tu bénéficies au titre du droit de ta résidence habituelle.",
    odr: "Règlement en ligne des litiges",
    odrText:
      "En tant que consommateur dans l'Union européenne, tu peux également recourir à la plateforme de Règlement en ligne des litiges (RLL) de la Commission européenne pour tenter de résoudre les différends à l'amiable. La plateforme RLL est accessible à :",
    odrUrl: "https://ec.europa.eu/consumers/odr",
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
      consentPrefix: "En cliquant Payer, tu acceptes les",
      consentTermsLink: "Conditions d'utilisation",
      consentJoin: " et la ",
      consentPrivacyLink: "Politique de confidentialité",
      consentSuffix: ". Événement de loisir à date fixe → pas de droit de rétractation 14j (art. 16(l) de la directive 2011/83/UE).",
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
      "Tu peux supprimer ton compte Poule Party et toutes les données qui y sont rattachées. Cette page est le point de suppression accessible sur le web exigé par Google Play — la même action est aussi disponible dans l'app via Paramètres → Supprimer mon compte.",
    dataDeletedTitle: "Ce qui est supprimé immédiatement",
    dataDeleted: [
      "Ton compte utilisateur anonyme Firebase Auth",
      "Ton document de profil /users/{uid} (pseudo + token de notifications push)",
    ],
    dataKeptTitle: "Ce qui est conservé",
    dataKept: [
      "Les parties passées auxquelles tu as participé conservent le nom d'équipe que tu y as utilisé (visible uniquement par les participants de la même session et par le créateur de la partie). C'est nécessaire à l'intégrité de l'historique des parties.",
      "Les analytics et rapports de crash anonymes (agrégés, non liés à ton identité).",
      "Les inscriptions à des événements payants sont conservées 12 mois après l'événement pour la comptabilité, la résolution des litiges et les obligations fiscales (voir la politique de confidentialité).",
    ],
    howTitle: "Tu veux un effacement manuel complet ?",
    howText:
      "Si tu veux que les noms d'équipe attachés à tes parties passées soient remplacés par un libellé générique, écris-nous. Inclus :",
    howList: [
      "Ton pseudo ou ton nom d'équipe (tel qu'il apparaissait dans l'app)",
      "La date approximative de ta dernière partie, si tu t'en souviens",
      "Objet : « Suppression de mon compte Poule Party »",
    ],
    timeframe:
      "Nous traitons les demandes d'effacement manuel sous 30 jours (en général sous une semaine). Tu recevras un email de confirmation dès que c'est fait.",
    emailButton: "Envoyer une demande d'effacement manuel",
    backHome: "Retour à l'accueil",
    formTitle: "Demander un effacement",
    formSubtitle: "Remplis ce formulaire et on le traitera sous 30 jours.",
    formEmailLabel: "Ton email",
    formEmailPlaceholder: "jeanne@mail.com",
    formNicknameLabel: "Pseudo ou nom d'équipe (facultatif)",
    formNicknamePlaceholder: "Tel qu'il apparaissait dans l'app",
    formReasonLabel: "Autre chose à préciser ? (facultatif)",
    formReasonPlaceholder: "Date approximative de la dernière partie, etc.",
    formSubmit: "ENVOYER LA DEMANDE",
    formSubmitting: "ENVOI EN COURS…",
    formSuccess:
      "C'est noté — on a bien reçu ta demande et on la traitera sous 30 jours. Une copie a été envoyée à {email}.",
    formErrorGeneric:
      "Quelque chose a planté. Réessaie ou écris-nous à julien@rahier.dev.",
    formErrorInvalidEmail: "Merci de saisir une adresse email valide.",
    fallbackHint: "Le formulaire ne marche pas ? Tu peux aussi nous écrire directement :",
  },
};
