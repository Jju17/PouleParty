/**
 * One-off script to fill the `en` / `nl` translations for every
 * challenge in `seedChallenges.ts`. Merges the two locale keys into
 * the existing `titleByLocale` / `bodyByLocale` maps without touching
 * the `fr` entry already in place.
 *
 * Already-populated `en` / `nl` keys are left alone (idempotent re-run).
 *
 * Usage (from the functions/ directory):
 *
 *   FIREBASE_PROJECT_ID=pouleparty-ba586 npx ts-node src/fillChallengeTranslations.ts
 *
 *   # or with an explicit SA:
 *   FIREBASE_SERVICE_ACCOUNT=/path/to/sa.json npx ts-node src/fillChallengeTranslations.ts
 */

import * as admin from "firebase-admin";
import * as fs from "fs";
import * as path from "path";

interface Translation {
  title: string;
  body: string;
}

interface ChallengeTranslations {
  id: string;
  en: Translation;
  nl: Translation;
}

const TRANSLATIONS: ChallengeTranslations[] = [
  {
    id: "street-brabanconne",
    en: { title: "Sing the Brabançonne", body: "In the middle of the Grand-Place." },
    nl: { title: "Zing de Brabançonne", body: "Midden op de Grote Markt." },
  },
  {
    id: "street-pyramide-humaine",
    en: { title: "Human pyramid", body: "Form a human pyramid with the team." },
    nl: { title: "Menselijke piramide", body: "Vorm een menselijke piramide met het team." },
  },
  {
    id: "street-demande-mariage",
    en: {
      title: "Marriage proposal",
      body: "Propose to a stranger — on one knee, the full deal.",
    },
    nl: {
      title: "Huwelijksaanzoek",
      body: "Vraag een onbekende ten huwelijk — op één knie, zoals het hoort.",
    },
  },
  {
    id: "street-photo-famille",
    en: {
      title: "Family photo",
      body: "Convince a stranger to pose for a family photo with the whole team.",
    },
    nl: {
      title: "Familiefoto",
      body: "Overtuig een onbekende om een familiefoto te nemen met het hele team.",
    },
  },
  {
    id: "street-couverture-album",
    en: {
      title: "Album cover",
      body: "Recreate the cover of a famous album with the team.",
    },
    nl: {
      title: "Albumhoes",
      body: "Reconstrueer de hoes van een bekend album met de teamleden.",
    },
  },
  {
    id: "street-selfie-police",
    en: {
      title: "Selfie with a cop",
      body: "Take a selfie with a police officer or security guard.",
    },
    nl: {
      title: "Selfie met een politieagent",
      body: "Maak een selfie met een politieagent of een beveiliger.",
    },
  },
  {
    id: "street-ecart-age",
    en: {
      title: "Age gap",
      body: "Find the biggest age gap between two strangers — photo together.",
    },
    nl: {
      title: "Leeftijdsverschil",
      body: "Vind het grootste leeftijdsverschil tussen twee onbekenden — foto samen.",
    },
  },
  {
    id: "street-nicolas-poule",
    en: {
      title: "Nicolas does the chicken",
      body: "Find a Nicolas and get him to do a chicken cluck. ID required on the proof.",
    },
    nl: {
      title: "Nicolas doet de kip",
      body: "Vind een Nicolas en laat hem een kippengeluid maken. Identiteitsbewijs verplicht op het bewijs.",
    },
  },
  {
    id: "street-calin-inconnu",
    en: { title: "10-second hug", body: "Give a 10-second hug to a stranger." },
    nl: { title: "Knuffel van 10 seconden", body: "Geef een onbekende een knuffel van 10 seconden." },
  },
  {
    id: "street-photo-poubelle",
    en: { title: "Trash photo", body: "Take a photo inside or behind a trash bin." },
    nl: { title: "Vuilnisbakfoto", body: "Maak een foto in of achter een vuilnisbak." },
  },
  {
    id: "street-commercant-poule",
    en: {
      title: "Shopkeeper chicken",
      body: "Convince a shopkeeper to make a chicken sound.",
    },
    nl: {
      title: "Winkelier-kip",
      body: "Overtuig een winkelier om een kippengeluid te maken.",
    },
  },
  {
    id: "street-parapluie",
    en: {
      title: "Umbrella crossing",
      body: "Walk a stranger across the street under an umbrella (even if it's not raining).",
    },
    nl: {
      title: "Paraplu-oversteek",
      body: "Begeleid een onbekende over straat met een paraplu (ook als het niet regent).",
    },
  },
  {
    id: "bar-mime-poule",
    en: {
      title: "The whole bar mimes the chicken",
      body: "Get the whole bar to do the chicken sound at the same time.",
    },
    nl: {
      title: "De hele bar doet de kip na",
      body: "Laat de hele bar tegelijk een kippengeluid maken.",
    },
  },
  {
    id: "bar-verre-offert",
    en: {
      title: "Drink bought by a stranger",
      body: "Convince someone at the bar to buy a drink for a team member.",
    },
    nl: {
      title: "Drankje van een onbekende",
      body: "Overtuig iemand in de bar om een drankje te kopen voor een teamlid.",
    },
  },
  {
    id: "bar-barman-danse",
    en: { title: "Dancing bartender", body: "Get the bartender to dance." },
    nl: { title: "Dansende barman", body: "Laat de barman dansen." },
  },
  {
    id: "bar-shot-gratuit",
    en: {
      title: "Free shot",
      body: "Negotiate a free shot from the bartender in under 30 seconds.",
    },
    nl: {
      title: "Gratis shot",
      body: "Onderhandel binnen 30 seconden een gratis shot van de barman.",
    },
  },
  {
    id: "bar-prenom-oiseau",
    en: {
      title: "Bird-name first name",
      body: "Take a photo with someone whose first name is a bird (Robin, Martin, Jay...).",
    },
    nl: {
      title: "Vogelnaam-voornaam",
      body: "Maak een foto met iemand met een vogelnaam als voornaam (Robin, Martin, Jay...).",
    },
  },
  {
    id: "bar-commande-chantee",
    en: { title: "Sung order", body: "Order a round by singing the order." },
    nl: { title: "Gezongen bestelling", body: "Bestel een rondje door de bestelling te zingen." },
  },
  {
    id: "bar-pierre-feuille-ciseaux",
    en: {
      title: "Rock-paper-scissors",
      body: "Rock-paper-scissors with a stranger — the loser pays for the drink.",
    },
    nl: {
      title: "Steen-papier-schaar",
      body: "Steen-papier-schaar met een onbekende — de verliezer betaalt het drankje.",
    },
  },
  {
    id: "bar-meme-mois",
    en: {
      title: "Same birth month",
      body: "Find someone born in the same month as you. ID required on the proof.",
    },
    nl: {
      title: "Zelfde geboortemaand",
      body: "Vind iemand die in dezelfde maand geboren is als jij. Identiteitsbewijs verplicht op het bewijs.",
    },
  },
  {
    id: "special-troc-oeuf",
    en: {
      title: "Egg trade",
      body: "Trade the egg you got at the start, step by step, to end up with the most valuable object possible. Validated by the Chicken at the end of the game.",
    },
    nl: {
      title: "Eieren-ruil",
      body: "Ruil het ei dat je aan de start kreeg stap voor stap totdat je het meest waardevolle voorwerp hebt. Wordt aan het einde van het spel gevalideerd door de Kip.",
    },
  },
];

function resolveProjectAndInitAdmin(): string {
  const explicitSaPath = process.env.FIREBASE_SERVICE_ACCOUNT;
  const envProjectId = process.env.FIREBASE_PROJECT_ID;
  const defaultSaPath = path.resolve(__dirname, "..", "service-account.json");

  if (explicitSaPath) {
    const sa = JSON.parse(fs.readFileSync(explicitSaPath, "utf8"));
    if (envProjectId && envProjectId !== sa.project_id) {
      console.error(
        `Refusing to run: FIREBASE_SERVICE_ACCOUNT targets "${sa.project_id}" ` +
          `but FIREBASE_PROJECT_ID is "${envProjectId}". Unset one or align them.`
      );
      process.exit(1);
    }
    admin.initializeApp({
      credential: admin.credential.cert(sa),
      projectId: sa.project_id,
    });
    return sa.project_id;
  }

  if (envProjectId) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: envProjectId,
    });
    return envProjectId;
  }

  if (fs.existsSync(defaultSaPath)) {
    const sa = JSON.parse(fs.readFileSync(defaultSaPath, "utf8"));
    console.warn(
      `⚠️  Using default ./service-account.json (project "${sa.project_id}"). ` +
        `Set FIREBASE_PROJECT_ID=<project> to override and silence this warning.`
    );
    admin.initializeApp({
      credential: admin.credential.cert(sa),
      projectId: sa.project_id,
    });
    return sa.project_id;
  }

  console.error(
    "No credentials found. Set FIREBASE_PROJECT_ID=<project> with ADC, " +
      "FIREBASE_SERVICE_ACCOUNT=/path/to/sa.json, or place service-account.json in functions/."
  );
  process.exit(1);
}

async function main() {
  const projectId = resolveProjectAndInitAdmin();
  const db = admin.firestore();
  console.log(`Filling en/nl translations in project "${projectId}"...\n`);

  let updated = 0;
  let skipped = 0;
  let missing = 0;

  for (const t of TRANSLATIONS) {
    const ref = db.collection("challenges").doc(t.id);
    const snap = await ref.get();
    if (!snap.exists) {
      console.warn(`  ✗ ${t.id}: not found in Firestore, skipping`);
      missing += 1;
      continue;
    }
    const data = snap.data() ?? {};
    const titleMap =
      typeof data.titleByLocale === "object" && data.titleByLocale
        ? (data.titleByLocale as Record<string, string>)
        : {};
    const bodyMap =
      typeof data.bodyByLocale === "object" && data.bodyByLocale
        ? (data.bodyByLocale as Record<string, string>)
        : {};

    // A doc is "untranslated" when the en/nl slot is empty OR carries
    // the same string as fr — that's the naive-copy signature left by
    // migrateChallengesV2 when no real translation existed yet.
    const titleNeeds = (loc: "en" | "nl") =>
      !titleMap[loc] || titleMap[loc].length === 0 || titleMap[loc] === titleMap.fr;
    const bodyNeeds = (loc: "en" | "nl") =>
      !bodyMap[loc] || bodyMap[loc].length === 0 || bodyMap[loc] === bodyMap.fr;

    const updates: Record<string, string> = {};
    if (titleNeeds("en")) updates["titleByLocale.en"] = t.en.title;
    if (titleNeeds("nl")) updates["titleByLocale.nl"] = t.nl.title;
    if (bodyNeeds("en")) updates["bodyByLocale.en"] = t.en.body;
    if (bodyNeeds("nl")) updates["bodyByLocale.nl"] = t.nl.body;

    if (Object.keys(updates).length === 0) {
      console.log(`  · ${t.id}: en + nl already filled`);
      skipped += 1;
      continue;
    }

    await ref.update(updates);
    console.log(`  • ${t.id}: ${Object.keys(updates).join(", ")}`);
    updated += 1;
  }

  console.log(
    `\n✅ Done. ${updated} updated, ${skipped} already-filled, ${missing} missing in Firestore.`
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
