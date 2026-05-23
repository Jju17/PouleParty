import { getFirestore } from "firebase-admin/firestore";
import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const REGION = "europe-west1";

type Locale = "fr" | "en" | "nl";

interface ChallengeDoc {
  id: string;
  points: number;
  type: "oneShot" | "repeatable";
  level: number;
  number: number;
  titleByLocale: Record<string, string>;
  bodyByLocale: Record<string, string>;
}

type Category = "street" | "bar" | "special" | "other";

function categoryOf(id: string): Category {
  if (id.startsWith("street-")) return "street";
  if (id.startsWith("bar-")) return "bar";
  if (id.startsWith("special-")) return "special";
  if (id.startsWith("chicken-")) return "special";
  return "other";
}

const CATEGORY_ORDER: Category[] = ["street", "bar", "special", "other"];

/**
 * Locale → text with a 2-level cascade matching the iOS/Android
 * `localizedTitle` accessor: requested locale, then `"fr"`. Empty
 * strings count as missing.
 */
function pickLocalized(map: Record<string, string>, locale: Locale): string {
  const v = map?.[locale];
  if (typeof v === "string" && v.length > 0) return v;
  const fr = map?.["fr"];
  if (typeof fr === "string" && fr.length > 0) return fr;
  return "";
}

interface SheetStrings {
  pageTitle: string;
  brandTitle: string;
  brandTagline: string;
  rulesHeading: string;
  rulesCountLabel: string;
  rulesIntro: string;
  rules: string[];
  objectiveHeading: string;
  objectiveTitle: string;
  objectiveSub: string;
  objectivePoints: string;
  objectivePointsLabel: string;
  whatsappHeading: string;
  whatsappTitle: string;
  whatsappBody: string;
  flipPrompt: string;
  challengesTitle: string;
  categoryHeadings: Record<Category, string>;
  categoryCountLabel: string;
  pointsSuffix: string;
  oneShotBadge: string;
  repeatableBadge: string;
  idBadge: string;
  idLegend: string;
  starLegend: string;
  proofLegend: string;
  goodHunt: string;
  emptyState: string;
}

const STRINGS: Record<Locale, SheetStrings> = {
  fr: {
    pageTitle: "PouleParty — Liste des défis",
    brandTitle: "POULE PARTY",
    brandTagline: "CHASSE A LA POULE URBAINE",
    rulesHeading: "LES REGLES DU JEU",
    rulesCountLabel: "REGLES",
    rulesIntro:
      "Une <strong>Poule</strong> est lâchée en ville avec quelques minutes d'avance. Ton objectif : la retrouver avant les autres équipes et accumuler un maximum de points en chemin.",
    rules: [
      "Formez des équipes de <strong>2 à 4 joueurs</strong>.",
      "La Poule se cache dans un <strong>bar ou lieu public</strong> de la ville.",
      "Une <strong>zone GPS</strong> autour d'elle se réduit progressivement — rapprochez-vous !",
      "Réalisez des <strong>défis</strong> pour accumuler des points bonus.",
      "Toute preuve doit être envoyée sur le <strong>groupe WhatsApp partagé</strong> pour valider les points.",
      "Pour les défis <span class=\"badge-id\">ID</span>, la pièce d'identité doit être <strong>visible sur la preuve</strong>.",
      "L'équipe avec le <strong>plus de points</strong> à la fin remporte la gloire éternelle.",
    ],
    objectiveHeading: "OBJECTIF PRINCIPAL",
    objectiveTitle: "Trouver la Poule",
    objectiveSub: "Première équipe à rejoindre la Poule",
    objectivePoints: "1000",
    objectivePointsLabel: "points",
    whatsappHeading: "REJOINS LA TROUPE",
    whatsappTitle: "Rejoins le groupe WhatsApp !",
    whatsappBody:
      "Demande le lien au bar de départ pour accéder au groupe partagé. Toutes tes preuves y seront envoyées pour valider les points.",
    flipPrompt: "Retourne la feuille pour découvrir tous les défis",
    challengesTitle: "Les défis",
    categoryHeadings: {
      street: "EN RUE",
      bar: "EN BAR",
      special: "DEFI SPECIAL",
      other: "AUTRES",
    },
    categoryCountLabel: "défis",
    pointsSuffix: "pts",
    oneShotBadge: "Défi",
    repeatableBadge: "Bar partenaire",
    idBadge: "ID",
    idLegend: "= pièce d'identité obligatoire sur la preuve",
    starLegend: "= validé en fin de partie",
    proofLegend: "Preuves sur le groupe WhatsApp partagé",
    goodHunt: "BONNE CHASSE !",
    emptyState: "Aucun défi pour le moment.",
  },
  en: {
    pageTitle: "PouleParty — Challenges sheet",
    brandTitle: "POULE PARTY",
    brandTagline: "URBAN CHICKEN HUNT",
    rulesHeading: "GAME RULES",
    rulesCountLabel: "RULES",
    rulesIntro:
      "A <strong>Chicken</strong> is let loose in the city with a few minutes' head start. Your mission: catch her before the other teams and rack up as many points as possible on the way.",
    rules: [
      "Form teams of <strong>2 to 4 players</strong>.",
      "The Chicken hides in a <strong>bar or public spot</strong> somewhere in the city.",
      "A <strong>GPS zone</strong> around her shrinks over time — close the distance!",
      "Pull off <strong>challenges</strong> to rack up bonus points.",
      "Every proof goes in the <strong>shared WhatsApp group</strong> to count.",
      "For <span class=\"badge-id\">ID</span> challenges, an ID must be <strong>visible on the proof</strong>.",
      "The team with the <strong>most points</strong> at the end takes eternal glory.",
    ],
    objectiveHeading: "MAIN OBJECTIVE",
    objectiveTitle: "Find the Chicken",
    objectiveSub: "First team to reach the Chicken",
    objectivePoints: "1000",
    objectivePointsLabel: "points",
    whatsappHeading: "JOIN THE CREW",
    whatsappTitle: "Join the WhatsApp group!",
    whatsappBody:
      "Ask for the invite at the start bar to access the shared group. Every proof goes there to count for points.",
    flipPrompt: "Flip the sheet to discover every challenge",
    challengesTitle: "Challenges",
    categoryHeadings: {
      street: "ON THE STREET",
      bar: "AT THE BAR",
      special: "SPECIAL CHALLENGE",
      other: "OTHER",
    },
    categoryCountLabel: "challenges",
    pointsSuffix: "pts",
    oneShotBadge: "Challenge",
    repeatableBadge: "Partner bar",
    idBadge: "ID",
    idLegend: "= ID card required, visible on the proof",
    starLegend: "= validated at the end of the game",
    proofLegend: "Proofs in the shared WhatsApp group",
    goodHunt: "HAPPY HUNTING !",
    emptyState: "No challenges yet.",
  },
  nl: {
    pageTitle: "PouleParty — Uitdagingenlijst",
    brandTitle: "POULE PARTY",
    brandTagline: "STEDELIJKE KIPPENJACHT",
    rulesHeading: "SPELREGELS",
    rulesCountLabel: "REGELS",
    rulesIntro:
      "Een <strong>Kip</strong> wordt losgelaten in de stad met een paar minuten voorsprong. Je doel: haar vinden vóór de andere teams en onderweg zoveel mogelijk punten verzamelen.",
    rules: [
      "Vorm teams van <strong>2 tot 4 spelers</strong>.",
      "De Kip verstopt zich in een <strong>bar of openbare plek</strong> in de stad.",
      "Een <strong>GPS-zone</strong> rond haar krimpt gestaag — kom dichterbij!",
      "Voltooi <strong>uitdagingen</strong> om bonuspunten te verzamelen.",
      "Elk bewijs gaat in de <strong>gedeelde WhatsApp-groep</strong> om te tellen.",
      "Voor <span class=\"badge-id\">ID</span>-uitdagingen moet het identiteitsbewijs <strong>zichtbaar zijn op het bewijs</strong>.",
      "Het team met de <strong>meeste punten</strong> aan het eind wint eeuwige glorie.",
    ],
    objectiveHeading: "HOOFDDOEL",
    objectiveTitle: "Vind de Kip",
    objectiveSub: "Eerste team dat bij de Kip aankomt",
    objectivePoints: "1000",
    objectivePointsLabel: "punten",
    whatsappHeading: "WORD ONDERDEEL",
    whatsappTitle: "Sluit je aan bij de WhatsApp-groep!",
    whatsappBody:
      "Vraag de uitnodiging aan de startbar voor toegang tot de gedeelde groep. Elk bewijs gaat daar om te tellen voor punten.",
    flipPrompt: "Draai het blad om en ontdek alle uitdagingen",
    challengesTitle: "Uitdagingen",
    categoryHeadings: {
      street: "OP STRAAT",
      bar: "IN DE BAR",
      special: "SPECIALE UITDAGING",
      other: "OVERIGE",
    },
    categoryCountLabel: "uitdagingen",
    pointsSuffix: "pnt",
    oneShotBadge: "Uitdaging",
    repeatableBadge: "Partnerbar",
    idBadge: "ID",
    idLegend: "= identiteitsbewijs verplicht, zichtbaar op het bewijs",
    starLegend: "= gevalideerd aan het einde van het spel",
    proofLegend: "Bewijzen in de gedeelde WhatsApp-groep",
    goodHunt: "GOEDE JACHT !",
    emptyState: "Nog geen uitdagingen.",
  },
};

const CHICKEN_PNG_BASE64 =
  "iVBORw0KGgoAAAANSUhEUgAAAeAAAAHMCAYAAAANunvYAAAJ8UlEQVR4nO3XwW0j2RlGUdHDJLxjFdde18aBSFobk0SvncQEICmQ3jAIVdGBNOiVARswNB7rdd9q6ZwAPvzqJnnxDne8y3yabiP31ut2GLm39/sAPqs/1QcAwGckwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAIHOsDfrT5NN1G7v12/GXk3N2vg+8DYJ+8gAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACBzrA/hPvx1/qU9406+n6TZyb71uh5F7AD8LL2AACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAKH+oDfM5+m28i9120dOcc7nad56N563Xb/mQa4u/MCBoCEAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgcBw9OJ+m28i9r1++jZwb7jzNQ/det3Xo3t6N/nvP0zz087det8PIPYB/8QIGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASBwnE/TbeTg1y/fRs7t3uu21icA8F+M7tt63Q4j97yAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAIHEYPzqfpNnLv65dvI+fu/vy3fwzd27vL5TJ0b1mWoXt7d57moXvrdRv+nYOPYnQ/nl6eR87dPd4/DN3zAgaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABIHAcPbhet8PIvb/+fbqN3Hv6y2Xk3O493j8M3Xvd1qF7ezf67z1P89DP8+jvG/wR82ns7/Po79vlsu/fey9gAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACx/qA37Net8PIvcf7h9vIvb17enmuT+DfvG7r0L3zNH+qz/NnM/r3bz5NQz8voz/Pn40XMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAgWN9wI+2XrfDyL35NN1G7j29PI+cu1uWZege+/K6rfUJfEfnad717wvv4wUMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0DgWB8A8FGcp3no3tPL89C9ZVmG7o12uVyG7j3ePwzdW6/bYeSeFzAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAIFjfcDPbr1uh5F7j/cPt5F7Ty/PI+fulmUZugcfyeu2Dt07T/PQvdFG/72fjRcwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDACBY30AjHSe5qF7r9s6dA/+iNGfv9HfD97HCxgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgMChPoDvaz5Nt5F7Ty/PI+fulmUZune5XIbuPd4/DN173dahe+zLeZrrE940+vO39+/bet123TgvYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAsf6ABhpWZahe08vz0P3ztM8dG+0122tT3jT3v/9Rn9e+Ni8gAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACBzrA2DPlmUZuvf08jx0b7TzNNcnvGnv/36jPy98bF7AABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEDvUB/Fzm03Qbuff08jxy7m5ZlqF7n83lcqlPeJP/3/cZ/f/7eP8wdG+9bp+qSV7AABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEDvUBfG7zabrVN7zldVvrE/iJXC6X+oQ3Pd4/DN1br5uGvIMXMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAgUN9AOzZfJpuI/eeXp5HzrEzj/cP9QlvWq+b3/wd8QIGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASBwqA+Az2Q+Tbf6Br6f9br5TeV/5gUMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0DgUB8A7Md8mm4j99brtuvfmM/297IvXsAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANAQIABICDAABAQYAAICDAABAQYAAICDAABAQaAgAADQOBYHwD8/+bTdKtv+JE+29/Lx+YFDAABAQaAgAADQECAASAgwAAQEGAACAgwAAQEGAACAgwAAQEGgIAAA0BAgAEgIMAAEBBgAAgIMAAEBBgAAgIMAAEBBoCAAANA4J8E0d+4Y/QDygAAAABJRU5ErkJggg==";

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * Joins the localized title + body into the row text. Most seed
 * challenges put the action in `body` and keep `title` as a short
 * label that's redundant once joined — so we render `body` when
 * present and fall back to `title` otherwise. The admin can rewrite
 * the body to read naturally on its own; the printed sheet only
 * shows the body string.
 */
function rowText(doc: ChallengeDoc, locale: Locale): string {
  const body = pickLocalized(doc.bodyByLocale, locale);
  if (body.length > 0) return body;
  return pickLocalized(doc.titleByLocale, locale);
}

function groupByCategory(docs: ChallengeDoc[]): Map<Category, ChallengeDoc[]> {
  const buckets = new Map<Category, ChallengeDoc[]>();
  for (const c of docs) {
    const cat = categoryOf(c.id);
    const list = buckets.get(cat) ?? [];
    list.push(c);
    buckets.set(cat, list);
  }
  for (const list of buckets.values()) {
    list.sort((a, b) => {
      if (b.points !== a.points) return b.points - a.points;
      return a.id.localeCompare(b.id);
    });
  }
  const ordered = new Map<Category, ChallengeDoc[]>();
  for (const cat of CATEGORY_ORDER) {
    const list = buckets.get(cat);
    if (list && list.length > 0) ordered.set(cat, list);
  }
  return ordered;
}

/**
 * Heuristic detection of ID-required challenges. Looks for an explicit
 * mention of an ID requirement in the localized body. Brittle by
 * design — once the data model gains a `requiresIdProof: Boolean`
 * field, replace this with a clean check.
 */
function requiresId(body: string): boolean {
  const v = body.toLowerCase();
  return (
    v.includes("pièce d'identité") ||
    v.includes("piece d'identite") ||
    v.includes("id required") ||
    v.includes("id card") ||
    v.includes("identiteitsbewijs")
  );
}

/**
 * Heuristic for "validated at end of game" challenges (special).
 */
function endOfGameValidation(body: string): boolean {
  const v = body.toLowerCase();
  return (
    v.includes("validé par la poule en fin") ||
    v.includes("valide par la poule en fin") ||
    v.includes("validated by the chicken at the end") ||
    v.includes("gevalideerd door de kip aan het ein")
  );
}

function renderCoverPage(s: SheetStrings): string {
  const rulesList = s.rules.map((line) => `<li>${line}</li>`).join("");
  return `
  <section class="cover">
    <header class="brand-head">
      <img class="logo" alt="" src="data:image/png;base64,${CHICKEN_PNG_BASE64}"/>
      <div class="brand-title-block">
        <h1 class="brand-title">${escapeHtml(s.brandTitle)}</h1>
        <div class="brand-tag">${escapeHtml(s.brandTagline)}</div>
      </div>
    </header>

    <section class="block rules">
      <h2 class="section-head">
        ${escapeHtml(s.rulesHeading)}
        <span class="section-tag">// ${s.rules.length} ${s.rulesCountLabel}</span>
      </h2>
      <p class="intro">${s.rulesIntro}</p>
      <ol>${rulesList}</ol>
    </section>

    <section class="block objective">
      <h2 class="section-head">
        ${escapeHtml(s.objectiveHeading)}
        <span class="section-tag">// ${escapeHtml(s.objectivePoints)} ${escapeHtml(s.objectivePointsLabel)}</span>
      </h2>
      <div class="callout objective-callout">
        <div class="callout-text">
          <div class="callout-title">${escapeHtml(s.objectiveTitle)}</div>
          <div class="callout-sub">${escapeHtml(s.objectiveSub)}</div>
        </div>
        <div class="callout-points">
          <div class="big-number">${escapeHtml(s.objectivePoints)}</div>
          <div class="big-label">${escapeHtml(s.objectivePointsLabel)}</div>
        </div>
      </div>
    </section>

    <section class="block whatsapp">
      <h2 class="section-head">
        ${escapeHtml(s.whatsappHeading)}
        <span class="section-tag">// PARTAGE</span>
      </h2>
      <div class="callout whatsapp-callout">
        <div class="callout-text">
          <div class="callout-title">${escapeHtml(s.whatsappTitle)}</div>
          <div class="callout-sub">${escapeHtml(s.whatsappBody)}</div>
        </div>
      </div>
    </section>

    <p class="flip-prompt">${escapeHtml(s.flipPrompt)} <span class="flip-arrow">↪</span></p>
  </section>`;
}

function renderChallengesPage(docs: ChallengeDoc[], lang: Locale, s: SheetStrings): string {
  const grouped = groupByCategory(docs);
  const idShown = new Set<string>();
  const starShown = new Set<string>();

  const sections = [...grouped.entries()]
    .map(([cat, list]) => {
      const cards = list
        .map((c) => {
          const title = pickLocalized(c.titleByLocale, lang);
          const body = pickLocalized(c.bodyByLocale, lang);
          const hasId = requiresId(body) || requiresId(title);
          const isStar = endOfGameValidation(body) || endOfGameValidation(title);
          if (hasId) idShown.add(c.id);
          if (isStar) starShown.add(c.id);
          const stripIdSentence = (t: string) =>
            t
              .replace(/\.?\s*Pièce d'identité obligatoire[^.]*\.?/i, "")
              .replace(/\.?\s*ID required[^.]*\.?/i, "")
              .replace(/\.?\s*ID card required[^.]*\.?/i, "")
              .replace(/\.?\s*Identiteitsbewijs verplicht[^.]*\.?/i, "")
              .trim();
          const cleanTitle = stripIdSentence(title);
          const cleanBody = stripIdSentence(body);
          const isRep = c.type === "repeatable";
          const numPrefix = c.number > 0
            ? `<span class="num">#${c.number}</span>`
            : "";
          const idBadge = hasId
            ? `<span class="badge-id">${escapeHtml(s.idBadge)}</span>`
            : "";
          const typeBadge = `<span class="badge-type ${isRep ? "rep" : "one"}">${escapeHtml(
            isRep ? s.repeatableBadge : s.oneShotBadge
          )}</span>`;
          const star = isStar ? `<span class="star">*</span>` : "";
          const bodyHtml = cleanBody.length > 0
            ? `<div class="card-body">${escapeHtml(cleanBody)}</div>`
            : "";
          return `
        <li class="challenge ${isRep ? "rep" : "one"}">
          <div class="card-main">
            <div class="card-title-row">
              ${numPrefix}
              <span class="card-title">${escapeHtml(cleanTitle)}${star}</span>
              ${idBadge}
              ${typeBadge}
            </div>
            ${bodyHtml}
          </div>
          <div class="card-pts">${c.points}<span class="pts-label">${escapeHtml(s.pointsSuffix)}</span></div>
        </li>`;
        })
        .join("");

      return `
    <section class="block category">
      <h2 class="section-head">
        ${escapeHtml(s.categoryHeadings[cat])}
        <span class="section-tag">// ${list.length} ${escapeHtml(s.categoryCountLabel)}</span>
      </h2>
      <ul>${cards}</ul>
    </section>`;
    })
    .join("");

  const legendParts: string[] = [];
  if (idShown.size > 0) {
    legendParts.push(`<span class="badge-id">${escapeHtml(s.idBadge)}</span> ${escapeHtml(s.idLegend)}`);
  }
  if (starShown.size > 0) {
    legendParts.push(`<span class="star">*</span> ${escapeHtml(s.starLegend)}`);
  }
  legendParts.push(escapeHtml(s.proofLegend));
  const legend = legendParts.join(" &nbsp; | &nbsp; ");

  const totalChallenges = docs.length;
  return `
  <section class="challenges-content">
    <header class="brand-head">
      <img class="logo" alt="" src="data:image/png;base64,${CHICKEN_PNG_BASE64}"/>
      <div class="brand-title-block">
        <h1 class="brand-title">${escapeHtml(s.challengesTitle)}</h1>
        <div class="brand-tag">// ${totalChallenges} ${escapeHtml(s.categoryCountLabel)} · ${escapeHtml(s.brandTagline)}</div>
      </div>
    </header>
    ${sections.length > 0 ? sections : `<p class="empty">${escapeHtml(s.emptyState)}</p>`}
    <footer class="bottom-footer">
      <div class="legend">${legend}</div>
      <div class="good-hunt">${escapeHtml(s.goodHunt)}</div>
    </footer>
  </section>`;
}

function resolveLocale(req: { path?: string; query?: Record<string, unknown> }): Locale {
  const q = typeof req.query?.lang === "string" ? req.query.lang.toLowerCase() : "";
  if (q === "fr" || q === "en" || q === "nl") return q;
  const path = (req.path ?? "").toLowerCase();
  if (path.includes("-fr")) return "fr";
  if (path.includes("-en")) return "en";
  if (path.includes("-nl")) return "nl";
  return "fr";
}

function renderHtml(docs: ChallengeDoc[], lang: Locale): string {
  const s = STRINGS[lang];
  return `<!DOCTYPE html>
<html lang="${lang}">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>${escapeHtml(s.pageTitle)}</title>
    <link href="https://fonts.googleapis.com/css2?family=Bangers&family=JetBrains+Mono:wght@400;500;700&family=Press+Start+2P&display=swap" rel="stylesheet" />
    <style>
      :root {
        color-scheme: light;
        --beige: #FDF9D5;
        --beige-warm: #FFE8C8;
        --surface: #FFFCEB;
        --ink: #1A1A2E;
        --ink-mute: #5A5A6E;
        --rule: rgba(26, 26, 46, 0.12);
        --orange: #FE6A00;
        --pink: #EF0778;
        --dark-card: #111422;
        --dark-card-fg: #F5F1DA;
      }
      * { box-sizing: border-box; }
      html, body { margin: 0; padding: 0; }
      body {
        background: radial-gradient(ellipse at center, var(--beige) 0%, var(--beige-warm) 100%);
        background-attachment: fixed;
        font-family: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
        color: var(--ink);
        font-size: 13px;
        line-height: 1.55;
        letter-spacing: 0.2px;
      }
      strong { font-weight: 700; }

      /* ── Page ── */
      .page {
        width: 210mm;
        min-height: 297mm;
        padding: 16mm 16mm 14mm;
        margin: 0 auto 24px;
        background: var(--beige);
        position: relative;
        display: flex;
        flex-direction: column;
      }

      /* ── Brand header (shared cover + challenges) ── */
      .brand-head {
        display: flex;
        align-items: center;
        gap: 18px;
        padding-bottom: 14px;
        margin-bottom: 18px;
        border-bottom: 2px solid rgba(254, 106, 0, 0.3);
      }
      .brand-head .logo {
        width: 72px;
        height: 72px;
        image-rendering: pixelated;
      }
      .brand-head .brand-title-block { flex: 1; min-width: 0; }
      .brand-title {
        margin: 0;
        font-family: 'Bangers', 'Arial Black', sans-serif;
        font-size: 56px;
        letter-spacing: 4px;
        background: linear-gradient(135deg, var(--orange), var(--pink));
        -webkit-background-clip: text;
        background-clip: text;
        -webkit-text-fill-color: transparent;
        color: transparent;
        line-height: 0.95;
        text-transform: uppercase;
      }
      .brand-tag {
        margin-top: 6px;
        font-family: 'Press Start 2P', monospace;
        font-size: 9px;
        letter-spacing: 2px;
        color: var(--ink-mute);
      }

      /* ── Section heading (shared all blocks) ── */
      .block { margin-bottom: 22px; page-break-inside: avoid; }
      .section-head {
        margin: 0 0 12px;
        font-family: 'Bangers', 'Arial Black', sans-serif;
        font-size: 26px;
        letter-spacing: 2px;
        color: var(--orange);
        display: flex;
        align-items: baseline;
        gap: 12px;
        flex-wrap: wrap;
      }
      .section-tag {
        font-family: 'Press Start 2P', monospace;
        font-size: 8px;
        letter-spacing: 1.5px;
        color: var(--ink-mute);
        opacity: 0.8;
        text-transform: uppercase;
      }

      /* ── Rules ── */
      .rules .intro { margin: 0 0 14px; font-size: 13px; line-height: 1.6; }
      .rules ol {
        padding-left: 0;
        margin: 0;
        list-style: none;
        counter-reset: rule-counter;
      }
      .rules li {
        position: relative;
        padding: 6px 0 6px 36px;
        counter-increment: rule-counter;
        line-height: 1.55;
        font-size: 12.5px;
      }
      .rules li::before {
        content: counter(rule-counter);
        position: absolute;
        left: 0;
        top: 4px;
        width: 24px;
        height: 24px;
        background: var(--orange);
        color: #fff;
        border-radius: 50%;
        font-family: 'Press Start 2P', monospace;
        font-size: 9px;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      /* ── Callout cards (dark) ── */
      .callout {
        background: var(--dark-card);
        color: var(--dark-card-fg);
        border-radius: 14px;
        padding: 18px 22px;
        display: flex;
        align-items: center;
        gap: 18px;
      }
      .callout-text { flex: 1; min-width: 0; }
      .callout-title {
        font-family: 'Bangers', 'Arial Black', sans-serif;
        font-size: 22px;
        letter-spacing: 1.5px;
        margin-bottom: 4px;
        color: var(--dark-card-fg);
      }
      .callout-sub {
        font-size: 11px;
        color: rgba(245, 241, 218, 0.7);
        line-height: 1.55;
      }
      .callout-points { text-align: right; flex-shrink: 0; }
      .big-number {
        font-family: 'Bangers', 'Arial Black', sans-serif;
        font-size: 48px;
        line-height: 0.9;
        letter-spacing: 2px;
        background: linear-gradient(135deg, var(--orange), var(--pink));
        -webkit-background-clip: text;
        background-clip: text;
        -webkit-text-fill-color: transparent;
        color: transparent;
      }
      .big-label {
        font-family: 'Press Start 2P', monospace;
        font-size: 8px;
        letter-spacing: 1.5px;
        color: rgba(245, 241, 218, 0.65);
        margin-top: 4px;
      }

      /* ── Flip prompt ── */
      .flip-prompt {
        margin-top: auto;
        padding-top: 16px;
        text-align: center;
        font-family: 'Press Start 2P', monospace;
        font-size: 9px;
        letter-spacing: 2px;
        color: var(--orange);
      }
      .flip-arrow {
        font-family: 'JetBrains Mono', monospace;
        font-size: 14px;
        margin-left: 8px;
        color: var(--pink);
      }

      /* ── Challenges (cards) ── */
      .category ul { list-style: none; margin: 0; padding: 0; }
      .challenge {
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 16px;
        align-items: center;
        padding: 12px 16px;
        margin-bottom: 8px;
        background: var(--surface);
        border-radius: 12px;
        border-left: 5px solid var(--orange);
        box-shadow: 0 2px 6px rgba(26, 26, 46, 0.06);
        page-break-inside: avoid;
      }
      .challenge.rep { border-left-color: var(--pink); }
      .card-main { min-width: 0; }
      .card-title-row {
        display: flex;
        align-items: baseline;
        gap: 8px;
        flex-wrap: wrap;
        line-height: 1.15;
      }
      .num {
        font-family: 'Bangers', 'Arial Black', sans-serif;
        font-size: 24px;
        letter-spacing: 1px;
        color: var(--orange);
        line-height: 0.95;
      }
      .card-title {
        font-family: 'Bangers', 'Arial Black', sans-serif;
        font-size: 20px;
        letter-spacing: 0.8px;
        color: var(--ink);
        line-height: 1.05;
      }
      .card-body {
        font-size: 12px;
        color: var(--ink);
        opacity: 0.85;
        margin-top: 4px;
        line-height: 1.5;
      }
      .badge-type, .badge-id {
        display: inline-block;
        font-family: 'Press Start 2P', monospace;
        font-size: 7px;
        letter-spacing: 1px;
        text-transform: uppercase;
        padding: 3px 7px;
        border-radius: 999px;
        color: #fff;
        white-space: nowrap;
        vertical-align: middle;
      }
      .badge-type.one { background: var(--orange); }
      .badge-type.rep { background: var(--pink); }
      .badge-id {
        background: var(--dark-card);
        color: var(--dark-card-fg);
        border-radius: 4px;
        padding: 3px 6px;
      }
      .star { color: var(--pink); font-weight: 700; margin-left: 2px; }
      .card-pts {
        font-family: 'Bangers', 'Arial Black', sans-serif;
        font-size: 26px;
        letter-spacing: 1px;
        color: var(--pink);
        white-space: nowrap;
        text-align: right;
        line-height: 1;
      }
      .card-pts .pts-label {
        display: block;
        font-family: 'Press Start 2P', monospace;
        font-size: 7px;
        margin-top: 4px;
        color: var(--ink-mute);
        letter-spacing: 1.5px;
      }

      /* ── Footer (challenges page) ── */
      .bottom-footer {
        margin-top: auto;
        padding-top: 20px;
        text-align: center;
        border-top: 2px solid rgba(254, 106, 0, 0.25);
      }
      .legend {
        font-size: 10.5px;
        color: var(--ink-mute);
        margin-bottom: 14px;
        line-height: 1.8;
      }
      .legend .badge-id, .legend .star { margin-right: 4px; }
      .good-hunt {
        font-family: 'Press Start 2P', monospace;
        font-size: 13px;
        letter-spacing: 4px;
        background: linear-gradient(135deg, var(--orange), var(--pink));
        -webkit-background-clip: text;
        background-clip: text;
        -webkit-text-fill-color: transparent;
        color: transparent;
      }

      .empty { text-align: center; padding: 48px 24px; font-family: 'Bangers', sans-serif; font-size: 22px; color: var(--ink-mute); }

      @media print {
        @page { size: A4; margin: 0; }
        body { background: var(--beige); }
        .page {
          margin: 0;
          padding: 14mm;
          box-shadow: none;
          background: var(--beige);
        }
        .challenges-content { page-break-before: always; }
        .callout, .challenge, .badge-id, .badge-type, .rules li::before {
          -webkit-print-color-adjust: exact;
          print-color-adjust: exact;
        }
        .brand-title, .big-number, .good-hunt {
          -webkit-print-color-adjust: exact;
          print-color-adjust: exact;
        }
      }
      @media screen {
        body { padding: 24px 0; }
        .page {
          box-shadow: 0 6px 24px rgba(0, 0, 0, 0.1);
          border-radius: 4px;
        }
      }
    </style>
  </head>
  <body>
    <div class="page">${renderCoverPage(s)}</div>
    <div class="page">${renderChallengesPage(docs, lang, s)}</div>
  </body>
</html>`;
}

export const renderChallengesSheet = onRequest(
  {
    region: REGION,
    cors: true,
    maxInstances: 5,
    concurrency: 20,
  },
  async (req, res) => {
    if (req.method !== "GET" && req.method !== "HEAD") {
      res.status(405).send("Method not allowed");
      return;
    }
    const lang = resolveLocale(req);
    try {
      const snap = await getFirestore().collection("challenges").get();
      const docs: ChallengeDoc[] = snap.docs.map((doc) => {
        const d = doc.data();
        const titleByLocale =
          d.titleByLocale && typeof d.titleByLocale === "object"
            ? (d.titleByLocale as Record<string, string>)
            : {};
        const bodyByLocale =
          d.bodyByLocale && typeof d.bodyByLocale === "object"
            ? (d.bodyByLocale as Record<string, string>)
            : {};
        return {
          id: doc.id,
          points: typeof d.points === "number" ? d.points : 0,
          type: d.type === "repeatable" ? "repeatable" : "oneShot",
          level: typeof d.level === "number" && d.level > 0 ? d.level : 1,
          number: typeof d.number === "number" ? d.number : 0,
          titleByLocale,
          bodyByLocale,
        };
      });

      res.set("Content-Type", "text/html; charset=utf-8");
      res.set("Cache-Control", "public, max-age=300, stale-while-revalidate=600");
      res.status(200).send(renderHtml(docs, lang));
    } catch (err) {
      logger.error("renderChallengesSheet failed", err);
      res.status(500).send("Internal error");
    }
  }
);
