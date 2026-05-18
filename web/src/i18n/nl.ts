export default {
  nav: {
    privacy: "Privacy",
    terms: "Voorwaarden",
    support: "Support",
  },
  home: {
    tagline:
      "Een GPS-verstoppertje op ware grootte! Eén speler is de Kip, de rest zijn Jagers. De zone krimpt, de jacht wordt heftiger!",
    appComingSoon: "App binnenkort beschikbaar...",
    downloadApp: "Download de app!",
    appStore: "App Store",
    googlePlay: "Google Play",
    androidDisclaimer:
      "⚠️ Android: de app zit in gesloten tests. Je e-mailadres moet bij de ontwikkelaar geregistreerd zijn voor je hem kan downloaden. Werkt het niet? Laat het ons weten in de WhatsApp-groep!",
    dDayEyebrow: "PouleParty D-Day",
    dDayTitle: "🐔 Zaterdag 6 juni",
    dDayBody: "Een Kip verstopt zich in Elsene. Jouw team moet haar opsporen. €12 / speler · Teams van 3 tot 5.",
    dDayCta: "SCHRIJF JE TEAM IN →",
  },
  privacy: {
    title: "Privacybeleid",
    lastUpdated: "Laatst bijgewerkt: 18 mei 2026",
    overview: "Verwerkingsverantwoordelijke",
    overviewText:
      "Poule Party (\"wij\", \"ons\", \"de app\") is een mobiele game op basis van locatie. De verwerkingsverantwoordelijke voor uw persoonsgegevens is:",
    controllerDetails: "Julien Rahier, julien@rahier.dev, België",
    dataCollected: "Welke gegevens we verzamelen",
    locationData: "Locatiegegevens:",
    locationDataText:
      "Uw GPS-coördinaten worden verzameld tijdens een actieve partij en gedeeld met de andere spelers in uw sessie. Het volgen van locatie stopt zodra de partij eindigt.",
    auth: "Anonieme authenticatie:",
    authText:
      "We gebruiken Firebase Anonymous Authentication om een anonieme gebruikers-ID voor uw toestel te genereren tijdens het spelen. Er is geen e-mail, naam of persoonlijk account vereist.",
    playerName: "Spelersnaam:",
    playerNameText:
      "De weergavenaam die u kiest wanneer u deelneemt aan een partij. Deze wordt lokaal op uw toestel opgeslagen en gedeeld met de andere spelers tijdens een spelsessie. Hij kan ook worden bewaard in de resultaten van de partij (bv. winnaars). Hij is niet gekoppeld aan enige persoonlijke identiteit.",
    analytics: "Analytics:",
    analyticsText:
      "We gebruiken Firebase Analytics om anonieme gebruiksgegevens te verzamelen (app-openingen, schermweergaves) en Firebase Crashlytics voor crashrapporten, om de app te verbeteren. Er worden via deze diensten geen persoonlijk identificeerbare gegevens verzameld.",
    paidEventData: "Inschrijving voor een betaald evenement:",
    paidEventDataText:
      "Wanneer je je inschrijft voor een betaald PouleParty-evenement via het webformulier (pouleparty.be/inschrijving), verzamelen we: de volledige naam van de kapitein, de teamnaam, het e-mailadres, het telefoonnummer, de teamgrootte, je IP-adres en taal, het Stripe Checkout-sessie-ID, en een validatiecode van 6 tekens die we voor het evenement genereren. Deze gegevens worden opgeslagen in onze Firestore-collectie `/eventRegistrations` en doorgegeven aan de hieronder vermelde derde partijen voor de betaling, het versturen van de bevestigingsmail en de logistiek ter plaatse. Betaalkaartgegevens worden nooit door ons verzameld — ze worden rechtstreeks ingevoerd op de door Stripe gehoste Checkout-pagina.",
    legalBasis: "Rechtsgrondslag voor de verwerking",
    legalBasisIntro:
      "Op grond van de AVG (art. 6) verwerken we uw gegevens op basis van de volgende rechtsgrondslagen:",
    legalBasisConsent: "Toestemming (art. 6, lid 1, onder a):",
    legalBasisConsentText:
      "Locatiegegevens: u geeft uitdrukkelijk toestemming voor locatie voordat er gegevens worden verzameld. U kunt uw toestemming op elk moment intrekken door de locatietoestemmingen in de instellingen van uw toestel te herroepen.",
    legalBasisLegitimate: "Gerechtvaardigd belang (art. 6, lid 1, onder f):",
    legalBasisLegitimateText:
      "Anonieme authenticatie en crashrapportage: noodzakelijk voor de werking van de app en het behoud van de servicekwaliteit. Ons gerechtvaardigd belang overstijgt uw rechten niet, aangezien de gegevens volledig anoniem zijn.",
    howWeUse: "Hoe we uw gegevens gebruiken",
    howWeUse1:
      "Locatiegegevens worden uitsluitend gebruikt voor de realtime spelmechanismen (posities op de kaart tonen, zonedetectie).",
    howWeUse2:
      "Locatiegegevens worden tijdens de partij opgeslagen in Firebase Firestore. Spelgegevens (inclusief locatiegeschiedenis) kunnen na afloop van de partij bewaard blijven voor operationele doeleinden en kunnen op verzoek worden verwijderd.",
    howWeUse3:
      "Analytische gegevens worden gebruikt om gebruikspatronen te begrijpen en bugs op te lossen.",
    thirdParties: "Diensten van derden",
    thirdPartiesIntro:
      "We gebruiken de volgende diensten van derden om de app te laten werken. Elk heeft zijn eigen privacybeleid:",
    thirdPartyFirebaseAnalytics: "Google Analytics for Firebase",
    thirdPartyFirebaseAnalyticsUrl: "https://firebase.google.com/support/privacy",
    thirdPartyCrashlytics: "Firebase Crashlytics",
    thirdPartyCrashlyticsUrl: "https://firebase.google.com/support/privacy/",
    thirdPartyMapbox: "Mapbox",
    thirdPartyMapboxUrl: "https://www.mapbox.com/legal/privacy",
    thirdPartyStripe: "Stripe (betalingsverwerking voor betaalde evenementen — Stripe Technology Europe Ltd in Ierland, met verdere doorgifte aan Stripe Inc. in de VS onder Standard Contractual Clauses)",
    thirdPartyStripeUrl: "https://stripe.com/privacy",
    thirdPartyResend: "Resend (transactionele bevestigingsmails voor betaalde evenementen — Resend Inc. in de VS onder Standard Contractual Clauses)",
    thirdPartyResendUrl: "https://resend.com/legal/privacy-policy",
    thirdPartyGoogleSheets: "Google Sheets (operationele deelnemerslijst voor on-site check-in op D-Day — Google LLC onder het EU-US Data Privacy Framework)",
    thirdPartyGoogleSheetsUrl: "https://policies.google.com/privacy",
    dataSharing: "Delen van gegevens",
    dataSharingText:
      "We verkopen, ruilen of delen uw persoonsgegevens niet met derden voor marketingdoeleinden. Locatiegegevens worden uitsluitend gedeeld met andere spelers in dezelfde spelsessie tijdens het actieve spel.",
    internationalTransfers: "Internationale gegevensoverdracht",
    internationalTransfersText:
      "Je gegevens worden verwerkt door Google Firebase-diensten, waarvan de servers zich buiten de Europese Economische Ruimte (EER) kunnen bevinden, onder meer in de Verenigde Staten. Google werkt in het kader van het EU-US Data Privacy Framework en de Standard Contractual Clauses (SCCs) om een adequaat niveau van gegevensbescherming te waarborgen zoals vereist door de AVG. Mapbox verwerkt kaartgegevens onder vergelijkbare waarborgen. Voor inschrijvingen voor betaalde evenementen: Stripe routeert de betalingen via Stripe Technology Europe Ltd (Ierland) en geeft geanonimiseerde risico-/fraudesignalen door aan Stripe Inc. (VS) onder SCC's. Resend (VS) verzendt de bevestigingsmail onder SCC's. De gegevens in Google Sheets (deelnemerslijst van het evenement) worden bewaard op de infrastructuur van Google LLC (VS) onder het EU-US Data Privacy Framework.",
    dataRetention: "Bewaren van gegevens",
    dataRetentionText:
      "Spelgegevens (inclusief locatiegeschiedenis) kunnen na afloop van een partij in Firebase Firestore bewaard blijven. Er vindt geen langdurig locatievolgen plaats buiten actieve partijen om. Inschrijvingen voor betaalde evenementen in `/eventRegistrations` worden tot 12 maanden na de datum van het evenement bewaard voor boekhouding, geschillenbeslechting en fiscale verplichtingen, en worden daarna gewist. Het Google Sheet dat wordt gebruikt voor de check-in ter plaatse wordt binnen 30 dagen na het evenement gewist. Je kan verzoeken om verwijdering van je spelgegevens door ons te contacteren op julien@rahier.dev. Anonieme authenticatietokens kunnen op je toestel blijven bestaan, maar kunnen worden gewist door je account te verwijderen via de app-instellingen of door de app te verwijderen.",
    cookies: "Cookies en vergelijkbare technologieën",
    cookiesText:
      "De publieke website gebruikt uitsluitend strikt noodzakelijke cookies: Firebase App Check voor bot-bescherming op het inschrijfformulier en de door Stripe gehoste cookies op de Stripe Checkout-pagina zelf (beheerd door Stripe, zie hun privacybeleid). We gebruiken geen advertentie-, analytische of tracking-cookies op de publieke webpagina's. Er wordt geen cookiebanner getoond omdat toestemming onder de ePrivacy-richtlijn niet vereist is voor strikt noodzakelijke cookies.",
    children: "Leeftijdsvereisten",
    childrenText:
      "De gratis mobiele game PouleParty wordt aanbevolen vanaf 13 jaar; gebruikers jonger dan 13 moeten het toestel laten configureren en de locatietoestemmingen laten verlenen door een ouder of voogd. Het betaalde PouleParty D-Day-evenement dat in openbare gelegenheden plaatsvindt (bars, de stad Brussel) is voorbehouden aan deelnemers van 18 jaar en ouder vanwege de vergunningsvereisten van de locatie. We verzamelen niet bewust persoonsgegevens van kinderen jonger dan 13 via de app, noch van personen jonger dan 18 via het inschrijfformulier voor het betaalde evenement. Als je vermoedt dat een minderjarige ons gegevens heeft verstrekt buiten deze grenzen, neem dan contact op via julien@rahier.dev zodat we deze kunnen verwijderen.",
    rights: "Uw rechten onder de AVG",
    rightsIntro:
      "Als gebruiker in de Europese Economische Ruimte heeft u de volgende rechten:",
    rightAccess: "Recht op inzage (art. 15):",
    rightAccessText:
      "U kunt een kopie opvragen van de persoonsgegevens die we over u bewaren.",
    rightRectification: "Recht op rectificatie (art. 16):",
    rightRectificationText: "U kunt verzoeken om correctie van onjuiste gegevens.",
    rightErasure: "Recht op gegevenswissing (art. 17):",
    rightErasureText:
      "Je kan verzoeken om verwijdering van je gegevens. De knop Instellingen > Verwijder mijn account in de app verwijdert onmiddellijk je anonieme Firebase Auth-account en je profieldocument `/users/{uid}`. Spellen waaraan je hebt deelgenomen (en de teamnaam die je daarvoor hebt gebruikt) worden onbeperkt bewaard omwille van de integriteit van de spelgeschiedenis — ze zijn enkel zichtbaar voor de deelnemers van dezelfde sessie en voor de spelmaker. Voor het volledig wissen van gegevens uit eerdere spellen (inclusief je teamnaam in winnaarslijsten) neem je contact op via julien@rahier.dev en handelen we de manuele wissing af binnen 30 dagen. Inschrijvingen voor betaalde evenementen worden op verzoek verwijderd (onder voorbehoud van de hierboven vermelde boekhoudkundige bewaartermijn van 12 maanden).",
    rightRestriction: "Recht op beperking (art. 18):",
    rightRestrictionText:
      "U kunt verzoeken dat we de verwerking van uw gegevens beperken.",
    rightPortability: "Recht op dataportabiliteit (art. 20):",
    rightPortabilityText:
      "Je kan je gegevens opvragen in een gestructureerd, machineleesbaar formaat. Stuur een verzoek naar julien@rahier.dev en we antwoorden binnen 30 dagen met een JSON-export die je profieldocument `/users/{uid}` bevat, de teamnamen en winnaarsrecords gekoppeld aan je anonieme gebruikers-ID over eerdere spellen, en — indien van toepassing — elke inschrijving voor een betaald evenement gekoppeld aan je e-mailadres.",
    rightObject: "Recht van bezwaar (art. 21):",
    rightObjectText:
      "U kunt bezwaar maken tegen de verwerking van uw gegevens op basis van gerechtvaardigd belang.",
    rightWithdraw: "Recht om toestemming in te trekken:",
    rightWithdrawText:
      "U kunt uw toestemming voor het volgen van locatie op elk moment intrekken door de locatietoestemmingen in de instellingen van uw toestel te herroepen.",
    rightsExercise:
      "Om een van deze rechten uit te oefenen, neem contact met ons op via julien@rahier.dev. We reageren binnen 30 dagen.",
    supervisory: "Toezichthoudende autoriteit",
    supervisoryText:
      "Als u van mening bent dat uw rechten op het gebied van gegevensbescherming zijn geschonden, heeft u het recht een klacht in te dienen bij uw lokale toezichthoudende autoriteit. In België is dat:",
    supervisoryAuthority: "Gegevensbeschermingsautoriteit (GBA)",
    supervisoryUrl: "https://www.gegevensbeschermingsautoriteit.be",
    contact: "Contact",
    contactText:
      "Als u vragen heeft over dit privacybeleid, neem dan contact met ons op via",
  },
  support: {
    title: "Support",
    needHelp: "Hulp nodig?",
    needHelpText:
      "Heb je problemen met Poule Party of een vraag? We helpen je graag verder.",
    commonIssues: "Veelvoorkomende problemen",
    locationTitle: "Locatie werkt niet",
    locationText:
      "Zorg dat je Poule Party toestemming hebt gegeven voor locatie in de instellingen van je toestel. De app heeft toegang \"Tijdens gebruik\" of \"Altijd\" nodig om te werken tijdens een partij.",
    joinTitle: "Kan geen partij joinen",
    joinText:
      "Controleer of de spelcode klopt (6 tekens). Het kan zijn dat de partij al gestart of afgelopen is. Vraag de maker van de partij om een nieuwe code.",
    mapTitle: "Kaart laadt niet",
    mapText:
      "Zorg voor een stabiele internetverbinding. Probeer de app te sluiten en opnieuw te openen.",
    contactTitle: "Contacteer ons",
    contactText:
      "Voor bugmeldingen, feature-verzoeken of andere vragen kun je ons bereiken op",
    contactFooter:
      "Voeg a.u.b. je toestelmodel, OS-versie en een beschrijving van het probleem toe zodat we je sneller kunnen helpen.",
  },
  terms: {
    title: "Gebruiksvoorwaarden",
    lastUpdated: "Laatst bijgewerkt: 18 mei 2026",
    acceptance: "Aanvaarding van de voorwaarden",
    acceptanceText:
      "Door Poule Party te downloaden, installeren of gebruiken, of door je in te schrijven voor een betaald PouleParty-evenement, ga je akkoord met deze gebruiksvoorwaarden. Als je niet akkoord gaat, gebruik de app dan niet en schrijf je niet in voor een evenement.",
    description: "Beschrijving van de dienst",
    descriptionText:
      "Poule Party is een gratis mobiele game op basis van locatie, waarbij één speler (de Kip) zich verstopt terwijl andere spelers (Jagers) haar proberen te vinden via een realtime kaart met een krimpende zone. Daarnaast organiseren we af en toe betaalde PouleParty-evenementen in persoon in Brussel (de \"D-Day\"-evenementenreeks), waarvoor tickets kunnen worden gekocht op het web via pouleparty.be/inschrijving; de in-app-ervaring zelf is en blijft gratis.",
    parties: "Contracterende partij",
    partiesText:
      "PouleParty wordt uitgebaat door Julien Rahier, in België geregistreerd als zelfstandige in eigen naam, contact julien@rahier.dev. Voor betaalde evenementen sluit je je contract rechtstreeks met Julien Rahier als organisator van het evenement. Er zit geen enkele vennootschap tussen jou en ons.",
    paidEvents: "Tickets voor betaalde evenementen",
    paidEventsPrice:
      "Tickets voor betaalde PouleParty-evenementen worden per speler verkocht aan €12 per speler, gefactureerd als €12 × de teamgrootte die je bij checkout kiest (3, 4 of 5 spelers). De prijzen zijn in EUR en omvatten de toepasselijke Belgische btw. De betaling wordt verwerkt door Stripe (zie het privacybeleid voor details); we ontvangen nooit je kaartgegevens.",
    paidEventsWhatsIncluded:
      "Elk ticket omvat: één toegang tot het gedateerde evenement (bv. PouleParty D-Day op zaterdag 6 juni 2026 vanaf 20u30 in Brussel/Elsene), één welkomstdrankje aan de startbar, en een polsbandje dat je op de eindlocatie ontvangt. Eten, extra drankjes, vervoer en eventuele bijkomende kosten zijn niet inbegrepen.",
    paidEventsWithdrawal:
      "Omdat PouleParty-evenementen vrijetijdsactiviteiten zijn die op een specifieke datum gepland zijn, is het 14-daagse herroepingsrecht uit artikel 9 van de Europese richtlijn consumentenrechten (2011/83/EU) NIET van toepassing — deze uitzondering is voorzien in artikel 16(l) van diezelfde richtlijn. Door je aankoop af te ronden erken je deze uitzondering uitdrukkelijk en doe je afstand van de bedenktermijn. Tickets zijn niet terugbetaalbaar, behalve in de hieronder vermelde gevallen.",
    paidEventsRefund:
      "Terugbetalingsregels: (a) als WIJ het evenement annuleren of verplaatsen om welke reden dan ook (weersomstandigheden, overmacht, onvoldoende inschrijvingen, beslissing van de organisator), ontvang je een volledige terugbetaling van de ticketprijs of, naar keuze, een overdracht naar de nieuwe datum; (b) als JIJ niet kan komen, is het ticket niet terugbetaalbaar — maar teams zijn vrij uitwisselbaar, dus je kan je plaats overdragen aan een andere deelnemer tot op de dag van het evenement door een e-mail te sturen naar julien@rahier.dev met de details van de vervanging; (c) als een deelnemer de toegang tot de locatie wordt geweigerd om redenen die hem toerekenbaar zijn (dronkenschap, weigering om veiligheidsinstructies te volgen, jonger dan 18), wordt geen terugbetaling gedaan.",
    paidEventsForceMajeure:
      "In geval van overmacht (bijvoorbeeld een pandemische volksgezondheidsmaatregel, ernstige weerswaarschuwing, terreurdreiging, sluiting van de locatie buiten onze wil) verplaatsen we het evenement naar een latere datum — waarbij alle tickets automatisch overgaan — of, als verplaatsing niet haalbaar is binnen 6 maanden, betalen we alle ticketbezitters volledig terug.",
    userConduct: "Gedrag van gebruikers",
    conduct1: "Gebruik de app uitsluitend voor het beoogde doel (het spelen van de game).",
    conduct2: "Probeer niet te valsspelen, te hacken of de app te reverse-engineeren.",
    conduct3: "Respecteer andere spelers en speel op veilige, openbare plekken.",
    conduct4: "U bent zelf verantwoordelijk voor uw veiligheid tijdens het spelen.",
    location: "Locatiegegevens",
    locationText:
      "De app heeft toegang tot de locatie nodig om te functioneren. Uw locatie wordt uitsluitend gedeeld met andere spelers in uw spelsessie tijdens het actieve spel. Zie ons privacybeleid voor meer details.",
    ugc: "Gebruikersinhoud & moderatie",
    ugcText:
      "Nicknames die je invoert zijn zichtbaar voor andere spelers in jouw spellen en in de leaderboards na afloop. Je gaat ermee akkoord geen aanstootgevende, beledigende, intimiderende, lasterlijke of seksuele nicknames te gebruiken, en evenmin namen die andermans identiteit misbruiken. De app bevat een automatisch profaniteitsfilter, maar je kan ook elke speler in het leaderboard rapporteren via de vlag-knop. We bekijken elk rapport en kunnen inhoud verwijderen, accounts hernoemen of recidivisten bannen. Misbruikers of kwaadwillige rapporteerders kunnen eveneens geband worden.",
    safety: "Fysieke veiligheid",
    safetyText:
      "Poule Party is een fysiek spel in de echte wereld. Let op het verkeer, respecteer privéterrein en speel nooit op een manier die jou, andere spelers of omstanders in gevaar brengt. Speel niet tijdens het autorijden of fietsen. Respecteer de lokale wetten. Je speelt op eigen risico.",
    disclaimer: "Disclaimer",
    disclaimerText:
      "De app wordt \"as is\" aangeboden, zonder enige garantie. We garanderen geen ononderbroken of foutloze dienst.",
    liability: "Beperking van aansprakelijkheid",
    liabilityText:
      "Julien Rahier is niet aansprakelijk voor schade die voortvloeit uit het gebruik van de app, met inbegrip van maar niet beperkt tot lichamelijk letsel, materiële schade of gegevensverlies.",
    termination: "Beëindiging",
    terminationText:
      "We behouden ons het recht voor om de toegang tot de app op elk moment en zonder voorafgaande kennisgeving op te schorten of te beëindigen, bij gedrag dat deze voorwaarden schendt.",
    changes: "Wijzigingen aan deze voorwaarden",
    changesText:
      "We kunnen deze voorwaarden van tijd tot tijd bijwerken. Het voortgezet gebruik van de app na wijzigingen geldt als aanvaarding van de nieuwe voorwaarden.",
    governingLaw: "Toepasselijk recht en bevoegde rechtbank",
    governingLawText:
      "Op deze voorwaarden is het Belgisch recht van toepassing. Elk geschil dat voortvloeit uit deze voorwaarden of uit je deelname aan een betaald PouleParty-evenement wordt uitsluitend voorgelegd aan de rechtbanken van het gerechtelijk arrondissement Brussel, België, onverminderd de niet voor afstand vatbare rechten inzake consumentenbescherming waarvan je geniet op grond van het recht van je gewone verblijfplaats.",
    odr: "Online geschillenbeslechting",
    odrText:
      "Als consument in de Europese Unie kan je ook gebruikmaken van het platform voor onlinegeschillenbeslechting (ODR) van de Europese Commissie om geschillen buitengerechtelijk op te lossen. Het ODR-platform is bereikbaar via:",
    odrUrl: "https://ec.europa.eu/consumers/odr",
    contact: "Contact",
    contactText:
      "Als u vragen heeft over deze voorwaarden, neem dan contact met ons op via",
  },
  createParty: {
    title: "Een feestje organiseren?",
    body: "PouleParty zit nog in beta. Wil je een spel organiseren of aanmaken? Contacteer Julien — we zetten het samen op poten.",
    contactEmailLabel: "E-mail:",
    contactWhatsAppLabel: "WhatsApp:",
  },
  footer: {
    rights: "Alle rechten voorbehouden.",
  },
  inscription: {
    intro: {
      eyebrow: "Echte GPS-verstoppertje",
      title: "Doe mee\nmet de jacht!",
      body: [
        "Een verklede Kip verstopt zich ergens in Elsene.",
        "Jij en je team moeten haar opsporen.",
        "De zone krimpt. De spanning stijgt. Vuile trucs toegestaan. 😈",
      ],
      priceLine: "€12 / persoon · Teams van 3 tot 5 · Za. 6 juni · 20u30",
      cta: "INSCHRIJVEN →",
    },
    form: {
      title: "Jouw team",
      subtitle: "Contactgegevens van de kapitein.",
      playerNameLabel: "Voor- & Achternaam",
      playerNamePlaceholder: "Jan Janssen",
      teamNameLabel: "Teamnaam",
      teamNamePlaceholder: "De Jagers",
      emailLabel: "E-mail",
      emailPlaceholder: "jan@mail.com",
      phoneLabel: "Telefoon",
      phonePlaceholder: "+32 470 ...",
      teamSizeLabel: "Teamgrootte",
      teamSizeUnit: "SPELERS",
      back: "← TERUG",
      next: "OVERZICHT →",
    },
    recap: {
      title: "Alles goed?",
      subtitle: "Controleer voor je betaalt.",
      captain: "Kapitein",
      team: "Team",
      email: "E-mail",
      phone: "Telefoon",
      players: "Spelers",
      total: "TOTAAL",
      note: "€12 / persoon · 1 gratis drankje aan de startbar · armband op de eindlocatie",
      paymentSecure: "Veilige betaling via Stripe (Kaart · Apple Pay · Google Pay)",
      consentPrefix: "Ik aanvaard de ",
      consentTermsLink: "Gebruiksvoorwaarden",
      consentJoin: " en het ",
      consentPrivacyLink: "Privacybeleid",
      consentSuffix: ". Ik begrijp dat dit een vrijetijdsevenement op een vaste datum is en dat het 14-daagse herroepingsrecht dus niet van toepassing is (art. 16(l) Richtlijn 2011/83/EU).",
      back: "← AANPASSEN",
      payButtonTemplate: "BETAAL {total} € 🔒",
      redirecting: "DOORVERWIJZEN…",
      defaultError: "Betaling kon niet starten. Probeer opnieuw of mail julien@rahier.dev.",
    },
    fatalError: {
      title: "Ongeldige inschrijflink",
      body: "Deze link bevat geen evenementreferentie. Ga terug naar het oorspronkelijke bericht of contacteer ons via julien@rahier.dev.",
    },
    success: {
      title: "Inschrijving\nbevestigd!",
      line1: "We kunnen niet wachten om je te zien op Poule Party #1!",
      line2: "Je krijgt een e-mail met je validatiecode en alle praktische info voor de dag zelf.",
      spamHint: "Geen e-mail na enkele minuten? Check je spam of mail",
      cluck: "TOK TOK 🐔",
    },
    cancel: {
      title: "Betaling\ngeannuleerd",
      body: "Er is niets afgeschreven. Je kan altijd opnieuw proberen.",
      retryButton: "INSCHRIJVING HERVATTEN",
    },
  },
  deleteAccount: {
    title: "Je Poule Party-account verwijderen",
    intro:
      "Je kan je Poule Party-account en alle gegevens die eraan gekoppeld zijn verwijderen. Deze pagina is het webtoegankelijke verwijderingsendpoint dat Google Play vereist — dezelfde actie is ook beschikbaar in de app via Instellingen → Verwijder mijn account.",
    dataDeletedTitle: "Wat onmiddellijk wordt verwijderd",
    dataDeleted: [
      "Je anonieme Firebase Auth-gebruikersaccount",
      "Je profieldocument /users/{uid} (nickname + push-notificatietoken)",
    ],
    dataKeptTitle: "Wat blijft bewaard",
    dataKept: [
      "Spellen waaraan je hebt deelgenomen behouden de teamnaam die je daarvoor hebt gebruikt (enkel zichtbaar voor de deelnemers van dezelfde sessie en voor de spelmaker). Dit is nodig voor de integriteit van de spelgeschiedenis.",
      "Anonieme analytics en crash-rapporten (geaggregeerd, niet gekoppeld aan jouw identiteit).",
      "Inschrijvingen voor betaalde evenementen worden 12 maanden na het evenement bewaard voor boekhouding, geschillenbeslechting en fiscale verplichtingen (zie het privacybeleid).",
    ],
    howTitle: "Wil je een volledige manuele wissing?",
    howText:
      "Als je wil dat de teamnamen die aan je vorige spellen hangen worden vervangen door een algemeen label, mail ons. Voeg toe:",
    howList: [
      "Je nickname of teamnaam (zoals die in de app verscheen)",
      "De geschatte datum van je laatste spel, indien je je die herinnert",
      "Onderwerp: \"Verwijder mijn Poule Party-account\"",
    ],
    timeframe:
      "We behandelen verzoeken tot manuele wissing binnen 30 dagen (meestal binnen een week). Je ontvangt een bevestigingsmail zodra het klaar is.",
    emailButton: "Stuur een verzoek tot manuele wissing",
    backHome: "Terug naar home",
  },
};
