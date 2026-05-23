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

/**
 * Locale → text with a 2-level cascade matching the iOS/Android
 * `localizedTitle` accessor: requested locale, then `"fr"` (FR-first
 * audience). Empty strings count as missing so a partially-populated
 * doc falls through cleanly. Returns `""` when both are missing —
 * the empty cell surfaces the bug so the admin populates the maps.
 */
function pickLocalized(
  map: Record<string, string>,
  locale: Locale
): string {
  const v = map?.[locale];
  if (typeof v === "string" && v.length > 0) return v;
  const fr = map?.["fr"];
  if (typeof fr === "string" && fr.length > 0) return fr;
  return "";
}

interface SheetStrings {
  pageTitle: string;
  sheetTitle: string;
  levelHeading: (level: number) => string;
  oneShotBadge: string;
  repeatableBadge: string;
  pointsSuffix: string;
  footer: string;
  emptyState: string;
}

const STRINGS: Record<Locale, SheetStrings> = {
  fr: {
    pageTitle: "PouleParty — Liste des défis",
    sheetTitle: "Les défis PouleParty",
    levelHeading: (n) => `Défis du niveau ${n}`,
    oneShotBadge: "Défi",
    repeatableBadge: "Bar partenaire",
    pointsSuffix: "pts",
    footer: "Bonne chasse à Bruxelles 🐔",
    emptyState: "Aucun défi pour le moment.",
  },
  en: {
    pageTitle: "PouleParty — Challenges sheet",
    sheetTitle: "PouleParty challenges",
    levelHeading: (n) => `Level ${n} challenges`,
    oneShotBadge: "Challenge",
    repeatableBadge: "Partner bar",
    pointsSuffix: "pts",
    footer: "Happy hunting in Brussels 🐔",
    emptyState: "No challenges yet.",
  },
  nl: {
    pageTitle: "PouleParty — Uitdagingenlijst",
    sheetTitle: "PouleParty uitdagingen",
    levelHeading: (n) => `Niveau ${n} uitdagingen`,
    oneShotBadge: "Uitdaging",
    repeatableBadge: "Partnerbar",
    pointsSuffix: "pnt",
    footer: "Veel jachtgenoegen in Brussel 🐔",
    emptyState: "Nog geen uitdagingen.",
  },
};

function resolveLocale(req: { path?: string; query?: Record<string, unknown> }): Locale {
  const q = typeof req.query?.lang === "string" ? req.query.lang.toLowerCase() : "";
  if (q === "fr" || q === "en" || q === "nl") return q;
  const path = (req.path ?? "").toLowerCase();
  if (path.includes("-fr")) return "fr";
  if (path.includes("-en")) return "en";
  if (path.includes("-nl")) return "nl";
  return "fr";
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function groupAndSort(docs: ChallengeDoc[]): Map<number, ChallengeDoc[]> {
  const byLevel = new Map<number, ChallengeDoc[]>();
  for (const c of docs) {
    const bucket = byLevel.get(c.level) ?? [];
    bucket.push(c);
    byLevel.set(c.level, bucket);
  }
  const sorted = new Map<number, ChallengeDoc[]>();
  for (const level of [...byLevel.keys()].sort((a, b) => a - b)) {
    const list = byLevel.get(level) ?? [];
    list.sort((a, b) => {
      if (a.number !== b.number) return a.number - b.number;
      return a.id.localeCompare(b.id);
    });
    sorted.set(level, list);
  }
  return sorted;
}

function renderHtml(docs: ChallengeDoc[], lang: Locale): string {
  const s = STRINGS[lang];
  const grouped = groupAndSort(docs);

  const sections =
    grouped.size === 0
      ? `<p class="empty">${escapeHtml(s.emptyState)}</p>`
      : [...grouped.entries()]
          .map(([level, list]) => {
            const items = list
              .map((c) => {
                const isRep = c.type === "repeatable";
                const badge = isRep ? s.repeatableBadge : s.oneShotBadge;
                const num = c.number > 0 ? `#${c.number}` : "";
                const title = pickLocalized(c.titleByLocale, lang);
                const body = pickLocalized(c.bodyByLocale, lang);
                return `
              <li class="challenge ${isRep ? "rep" : "one"}">
                <div class="num">${escapeHtml(num)}</div>
                <div class="body">
                  <div class="title-row">
                    <span class="title">${escapeHtml(title)}</span>
                    <span class="badge">${escapeHtml(badge)}</span>
                  </div>
                  <div class="desc">${escapeHtml(body)}</div>
                </div>
                <div class="pts">${c.points} ${escapeHtml(s.pointsSuffix)}</div>
              </li>`;
              })
              .join("");
            return `
        <section class="level">
          <h2>${escapeHtml(s.levelHeading(level))}</h2>
          <ul>${items}</ul>
        </section>`;
          })
          .join("");

  return `<!DOCTYPE html>
<html lang="${lang}">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>${escapeHtml(s.pageTitle)}</title>
    <style>
      :root { color-scheme: light; }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        padding: 24px;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
        color: #1a1a1a;
        background: #fff;
      }
      header {
        text-align: center;
        margin-bottom: 24px;
        border-bottom: 4px solid #FE6A00;
        padding-bottom: 12px;
      }
      header h1 {
        margin: 0;
        font-size: 28px;
        letter-spacing: 1px;
        color: #FE6A00;
      }
      .level { page-break-inside: avoid; margin-bottom: 28px; }
      .level h2 {
        font-size: 18px;
        text-transform: uppercase;
        letter-spacing: 1px;
        margin: 0 0 12px;
        color: #1a1a1a;
        border-left: 6px solid #EF0778;
        padding-left: 10px;
      }
      ul { list-style: none; margin: 0; padding: 0; }
      .challenge {
        display: grid;
        grid-template-columns: 56px 1fr 72px;
        gap: 12px;
        align-items: start;
        padding: 10px 12px;
        border: 1px solid #e2e2e2;
        border-radius: 8px;
        margin-bottom: 8px;
        page-break-inside: avoid;
      }
      .challenge.rep { border-left: 4px solid #007AFF; }
      .challenge.one { border-left: 4px solid #FE6A00; }
      .num {
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 22px;
        font-weight: 700;
        color: #FE6A00;
        text-align: right;
      }
      .title-row {
        display: flex;
        align-items: baseline;
        gap: 8px;
        flex-wrap: wrap;
      }
      .title {
        font-size: 16px;
        font-weight: 700;
      }
      .badge {
        font-size: 10px;
        text-transform: uppercase;
        letter-spacing: 1px;
        padding: 2px 6px;
        border-radius: 4px;
        background: #f3f3f3;
        color: #555;
      }
      .challenge.rep .badge { background: #e6f0ff; color: #003e87; }
      .challenge.one .badge { background: #ffe6d1; color: #8a3500; }
      .desc {
        font-size: 13px;
        color: #444;
        margin-top: 4px;
        line-height: 1.4;
      }
      .pts {
        font-size: 14px;
        font-weight: 700;
        color: #EF0778;
        text-align: right;
        white-space: nowrap;
      }
      footer {
        margin-top: 32px;
        text-align: center;
        font-size: 12px;
        color: #888;
      }
      .empty { text-align: center; padding: 32px; color: #888; font-style: italic; }
      @media print {
        @page { size: A4; margin: 12mm; }
        body { padding: 0; }
      }
    </style>
  </head>
  <body>
    <header><h1>${escapeHtml(s.sheetTitle)}</h1></header>
    ${sections}
    <footer>${escapeHtml(s.footer)}</footer>
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
