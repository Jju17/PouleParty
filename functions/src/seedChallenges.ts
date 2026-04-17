/**
 * One-off seed script to populate the global `/challenges` collection.
 *
 * Usage (from the functions/ directory):
 *
 *   # With a service-account JSON (prod uses ./service-account.json by default):
 *   npx ts-node src/seedChallenges.ts
 *   FIREBASE_SERVICE_ACCOUNT=/path/to/staging-sa.json npx ts-node src/seedChallenges.ts
 *
 *   # With Application Default Credentials (`gcloud auth application-default login`):
 *   FIREBASE_PROJECT_ID=pouleparty-ba586 npx ts-node src/seedChallenges.ts
 */

import * as admin from "firebase-admin";
import * as fs from "fs";
import * as path from "path";

type SeedChallenge = {
  id: string;
  title: string;
  body: string;
  points: number;
};

const challenges: SeedChallenge[] = [
  // EN RUE
  {
    id: "street-brabanconne",
    title: "Chanter la Brabançonne",
    body: "Au milieu de la Grand-Place.",
    points: 150,
  },
  {
    id: "street-pyramide-humaine",
    title: "Pyramide humaine",
    body: "Former une pyramide humaine avec l'équipe.",
    points: 100,
  },
  {
    id: "street-demande-mariage",
    title: "Demande en mariage",
    body: "Demander en mariage un(e) inconnu(e) — à genoux, en bonne et due forme.",
    points: 100,
  },
  {
    id: "street-photo-famille",
    title: "Photo famille",
    body: "Convaincre un inconnu de faire une photo famille avec toute l'équipe.",
    points: 80,
  },
  {
    id: "street-couverture-album",
    title: "Couverture d'album",
    body: "Recréer la couverture d'un album célèbre avec les membres de l'équipe.",
    points: 80,
  },
  {
    id: "street-selfie-police",
    title: "Selfie avec policier",
    body: "Faire un selfie avec un policier ou un agent de sécurité.",
    points: 80,
  },
  {
    id: "street-ecart-age",
    title: "Écart d'âge",
    body: "Trouver le plus grand écart d'âge entre deux inconnus — photo ensemble.",
    points: 70,
  },
  {
    id: "street-nicolas-poule",
    title: "Nicolas fait la poule",
    body: "Trouver un Nicolas et lui faire faire un bruit de poule. Pièce d'identité obligatoire sur la preuve.",
    points: 60,
  },
  {
    id: "street-calin-inconnu",
    title: "Câlin de 10 secondes",
    body: "Faire un câlin de 10 secondes à un inconnu.",
    points: 60,
  },
  {
    id: "street-photo-poubelle",
    title: "Photo poubelle",
    body: "Prendre une photo dans ou derrière une poubelle.",
    points: 50,
  },
  {
    id: "street-commercant-poule",
    title: "Commerçant poule",
    body: "Convaincre un commerçant de faire un bruit de poule.",
    points: 50,
  },
  {
    id: "street-parapluie",
    title: "Traversée parapluie",
    body: "Faire traverser un inconnu avec un parapluie (même s'il ne pleut pas).",
    points: 40,
  },
  // EN BAR
  {
    id: "bar-mime-poule",
    title: "Tout le bar mime la poule",
    body: "Faire mimer un bruit de poule à tout le bar en même temps.",
    points: 120,
  },
  {
    id: "bar-verre-offert",
    title: "Verre offert par un inconnu",
    body: "Convaincre quelqu'un au bar de payer un verre à un membre de l'équipe.",
    points: 100,
  },
  {
    id: "bar-barman-danse",
    title: "Barman qui danse",
    body: "Faire danser le barman.",
    points: 80,
  },
  {
    id: "bar-shot-gratuit",
    title: "Shot gratuit",
    body: "Négocier un shot gratuit au barman en moins de 30 secondes.",
    points: 80,
  },
  {
    id: "bar-prenom-oiseau",
    title: "Prénom d'oiseau",
    body: "Prendre une photo avec quelqu'un au prénom d'oiseau (Robin, Martin, Jay...).",
    points: 70,
  },
  {
    id: "bar-commande-chantee",
    title: "Commande chantée",
    body: "Commander une tournée en chantant la commande.",
    points: 60,
  },
  {
    id: "bar-pierre-feuille-ciseaux",
    title: "Pierre-feuille-ciseaux",
    body: "Pierre-feuille-ciseaux avec un inconnu — le perdant paye le verre.",
    points: 50,
  },
  {
    id: "bar-meme-mois",
    title: "Même mois de naissance",
    body: "Trouver quelqu'un né le même mois que toi. Pièce d'identité obligatoire sur la preuve.",
    points: 40,
  },
  // DEFI SPECIAL
  {
    id: "special-troc-oeuf",
    title: "Troc de l'œuf",
    body: "Échange ton œuf reçu au départ petit à petit pour arriver avec l'objet ayant la plus grande valeur. Validé par la Poule en fin de partie.",
    points: 300,
  },
];

async function main() {
  const explicitSaPath = process.env.FIREBASE_SERVICE_ACCOUNT;
  const defaultSaPath = path.resolve(__dirname, "..", "service-account.json");
  const saPath = explicitSaPath ?? (fs.existsSync(defaultSaPath) ? defaultSaPath : null);

  let projectId: string;
  if (saPath) {
    const serviceAccount = JSON.parse(fs.readFileSync(saPath, "utf8"));
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId: serviceAccount.project_id,
    });
    projectId = serviceAccount.project_id;
  } else if (process.env.FIREBASE_PROJECT_ID) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: process.env.FIREBASE_PROJECT_ID,
    });
    projectId = process.env.FIREBASE_PROJECT_ID;
  } else {
    console.error(
      "No credentials found. Set FIREBASE_SERVICE_ACCOUNT=/path/to/sa.json " +
        "or provide ./service-account.json, or set FIREBASE_PROJECT_ID with ADC."
    );
    process.exit(1);
  }

  const db = admin.firestore();
  console.log(`Seeding ${challenges.length} challenges into project "${projectId}"...`);

  const batch = db.batch();
  const now = admin.firestore.FieldValue.serverTimestamp();

  for (const challenge of challenges) {
    const ref = db.collection("challenges").doc(challenge.id);
    batch.set(
      ref,
      {
        title: challenge.title,
        body: challenge.body,
        points: challenge.points,
        lastUpdated: now,
      },
      { merge: true }
    );
  }

  await batch.commit();
  console.log(`Done. ${challenges.length} documents written to /challenges.`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
