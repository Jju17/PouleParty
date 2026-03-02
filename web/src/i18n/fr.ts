export default {
  nav: {
    privacy: "Confidentialité",
    support: "Support",
  },
  home: {
    tagline:
      "Un cache-cache GPS grandeur nature ! Un joueur est la Poule, les autres sont Chasseurs. La zone rétrécit, la traque s'intensifie !",
    appStore: "App Store",
    googlePlay: "Google Play",
  },
  privacy: {
    title: "Politique de confidentialité",
    lastUpdated: "Dernière mise à jour : 1er mars 2026",
    overview: "Présentation",
    overviewText:
      "Poule Party (\"nous\", \"notre\", \"l'application\") est un jeu mobile de géolocalisation développé par Julien Rahier. Cette politique explique comment nous collectons, utilisons et protégeons vos informations.",
    dataCollected: "Données collectées",
    locationData: "Données de localisation :",
    locationDataText:
      "Vos coordonnées GPS sont collectées pendant une partie et partagées avec les autres joueurs de votre session. Le suivi de localisation s'arrête à la fin de la partie.",
    auth: "Authentification anonyme :",
    authText:
      "Nous utilisons Firebase Anonymous Authentication pour identifier votre appareil pendant le jeu. Aucun email, nom ou compte personnel n'est requis.",
    playerName: "Nom du joueur :",
    playerNameText:
      "Le nom d'affichage que vous choisissez en rejoignant une partie. Il n'est lié à aucune identité personnelle.",
    analytics: "Analytiques :",
    analyticsText:
      "Nous utilisons Firebase Analytics pour collecter des données d'utilisation anonymes (ouvertures de l'app, vues d'écran, rapports de crash) afin d'améliorer l'application. Aucune information personnelle identifiable n'est collectée.",
    howWeUse: "Utilisation de vos données",
    howWeUse1:
      "Les données de localisation sont utilisées uniquement pour les mécaniques de jeu en temps réel (affichage des positions sur la carte, détection de zone).",
    howWeUse2:
      "Les données de localisation sont stockées temporairement dans Firebase Firestore pendant la durée de la partie et ne sont pas conservées après la fin du jeu.",
    howWeUse3:
      "Les données analytiques sont utilisées pour comprendre les habitudes d'utilisation et corriger les bugs.",
    dataSharing: "Partage des données",
    dataSharingText:
      "Nous ne vendons, n'échangeons ni ne partageons vos données personnelles avec des tiers. Les données de localisation sont uniquement partagées avec les autres joueurs de la même session. Nous utilisons les services Google Firebase (Firestore, Auth, Analytics) pour faire fonctionner l'application ; leur politique de confidentialité s'applique aux données traitées par leurs services.",
    dataRetention: "Conservation des données",
    dataRetentionText:
      "Les données de jeu (y compris l'historique de localisation) sont automatiquement supprimées à la fin de la partie. Aucun historique de localisation à long terme n'est conservé. Les jetons d'authentification anonyme peuvent persister sur votre appareil mais peuvent être supprimés en désinstallant l'application.",
    children: "Protection des mineurs",
    childrenText:
      "L'application ne s'adresse pas aux enfants de moins de 13 ans. Nous ne collectons pas sciemment d'informations personnelles auprès d'enfants de moins de 13 ans.",
    rights: "Vos droits",
    rightsText:
      "Vous pouvez arrêter de partager votre localisation à tout moment en fermant l'application ou en révoquant les permissions de localisation dans les paramètres de votre appareil. Vous pouvez demander la suppression de toute donnée associée à votre appareil en nous contactant.",
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
  footer: {
    rights: "Tous droits réservés.",
  },
};
