import { logger } from "firebase-functions/v2";

// PP-52 — Confirmation email sent by `confirmRegistrationPayment`
// (functions/src/registrations.ts) after Stripe confirms the payment.
// Plain HTML (no React Email build step) so the functions bundle
// stays small and CJS-friendly.
//
// Localized on `reg.locale` (`fr`, `en`, `nl`). Unknown locales fall
// back to `fr` since the D-Day audience is FR-first.
//
// Frames the 6-char code as a physical wristband-pickup token, NOT
// as an in-app join code. The mobile binaries shipped from 1.13.1
// (4) onward no longer query this code or claim the legacy
// `pouleparty.be/join?code=…` Universal Link / App Link — compliance
// with Apple App Store 3.1.1 (paid digital content). All in-app
// gameplay is free; the Stripe inscription is a real-world ticket
// for the in-person event in Ixelles.

interface RegistrationSnapshot {
  registrationId: string;
  batchId: string;
  playerName: string;
  teamName: string;
  email: string;
  teamSize: number;
  code: string;
  locale: string;
}

type Locale = "fr" | "en" | "nl";

interface EmailStrings {
  subject: string;
  headerSubtitle: string;
  greeting: (name: string) => string;
  body: (teamName: string, teamSize: number, dDay: string) => string;
  codeLabel: string;
  codeInstructions: string;
  support: string;
  reference: string;
  footer: string;
  dDay: string;
}

const FROM_ADDRESS = "PouleParty <noreply@pouleparty.be>";
const SUPPORT_EMAIL = "julien@rahier.dev";

const STRINGS: Record<Locale, EmailStrings> = {
  fr: {
    subject: "Ton inscription PouleParty est confirmée 🎉",
    headerSubtitle: "Inscription confirmée",
    greeting: (name) => `Salut <strong>${escapeHtml(name)}</strong>,`,
    body: (teamName, teamSize, dDay) =>
      `Ton paiement est passé, ton équipe <strong>« ${escapeHtml(teamName)} »</strong> (${teamSize} joueur·euse·s) est officiellement inscrite pour le ${dDay}. Rendez-vous au bar de départ à Ixelles à 20h30. Préparez vos baskets 🏃`,
    codeLabel: "TON CODE D'ENTRÉE",
    codeInstructions:
      "Présente ce code au bar de départ pour récupérer ton bracelet, ton verre de bienvenue, et le code de la partie qui sera annoncé sur place. Garde ce mail (ou note le code), c'est ton seul justificatif d'inscription.",
    support: "Une question&nbsp;? Écris à",
    reference: "Référence inscription",
    footer: "PouleParty — Bruxelles 🇧🇪",
    dDay: "samedi 6 juin 2026",
  },
  en: {
    subject: "Your PouleParty registration is confirmed 🎉",
    headerSubtitle: "Registration confirmed",
    greeting: (name) => `Hi <strong>${escapeHtml(name)}</strong>,`,
    body: (teamName, teamSize, dDay) =>
      `Payment confirmed. Your team <strong>"${escapeHtml(teamName)}"</strong> (${teamSize} players) is officially signed up for ${dDay}. See you at the start bar in Ixelles at 8:30 PM. Get those sneakers ready 🏃`,
    codeLabel: "YOUR ENTRY CODE",
    codeInstructions:
      "Show this code at the start bar to pick up your wristband, welcome drink, and the game code that will be announced on site. Keep this email (or note the code down). It is your only proof of registration.",
    support: "Anything wrong? Email",
    reference: "Registration reference",
    footer: "PouleParty — Brussels 🇧🇪",
    dDay: "Saturday, June 6, 2026",
  },
  nl: {
    subject: "Je PouleParty-inschrijving is bevestigd 🎉",
    headerSubtitle: "Inschrijving bevestigd",
    greeting: (name) => `Hallo <strong>${escapeHtml(name)}</strong>,`,
    body: (teamName, teamSize, dDay) =>
      `Betaling bevestigd. Je team <strong>"${escapeHtml(teamName)}"</strong> (${teamSize} spelers) staat officieel ingeschreven voor ${dDay}. Afspraak aan de startbar in Elsene om 20u30. Haal die loopschoenen maar boven 🏃`,
    codeLabel: "JE TOEGANGSCODE",
    codeInstructions:
      "Toon deze code aan de startbar om je polsbandje, welkomstdrankje en de spelcode op te halen die ter plaatse wordt aangekondigd. Bewaar deze e-mail (of noteer de code). Het is je enige bewijs van inschrijving.",
    support: "Een probleem? Mail naar",
    reference: "Inschrijvingsreferentie",
    footer: "PouleParty — Brussel 🇧🇪",
    dDay: "zaterdag 6 juni 2026",
  },
};

function pickStrings(locale: string): { strings: EmailStrings; lang: Locale } {
  if (locale === "en" || locale === "nl") return { strings: STRINGS[locale], lang: locale };
  return { strings: STRINGS.fr, lang: "fr" };
}

function renderHtml(reg: RegistrationSnapshot, s: EmailStrings, lang: Locale): string {
  // Email rendering notes:
  // 1. `color-scheme` + `supported-color-schemes` opt this email into the
  //    OS-level dark-mode handling for Apple Mail / iOS Mail / Outlook 2019+
  //    instead of letting them apply their own (ugly) inversion heuristic.
  // 2. The outer wrap stays neutral (white in light, dark gray in dark) so
  //    the card has a clean canvas in both modes.
  // 3. Brand orange + pink stay vibrant in both modes (no need to swap).
  return `<!DOCTYPE html>
<html lang="${lang}">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="color-scheme" content="light dark" />
    <meta name="supported-color-schemes" content="light dark" />
    <title>${escapeHtml(s.subject)}</title>
    <link href="https://fonts.googleapis.com/css2?family=Bangers&family=Press+Start+2P&display=swap" rel="stylesheet" />
    <style>
      :root { color-scheme: light dark; }
      .pp-wrap { background-color: #ffffff; }
      .pp-card { background-color: #ffffff; box-shadow: 0 6px 24px rgba(0,0,0,0.08); }
      .pp-text { color: #1f1f1f; }
      .pp-muted { color: #555; }
      .pp-faint { color: #999; }
      .pp-code-box { background-color: #FFE8C8; }
      .pp-code-label { color: #1f1f1f; }
      .pp-divider { border-top: 1px solid #eee; }
      @media (prefers-color-scheme: dark) {
        .pp-wrap { background-color: #1A1A2E !important; }
        .pp-card { background-color: #16213E !important; box-shadow: none !important; }
        .pp-text { color: #F0F0F0 !important; }
        .pp-muted { color: #C0C0C0 !important; }
        .pp-faint { color: #8A8A99 !important; }
        .pp-code-box { background-color: #0F1626 !important; }
        .pp-code-label { color: #F0F0F0 !important; }
        .pp-divider { border-top: 1px solid #2A2D43 !important; }
      }
    </style>
  </head>
  <body class="pp-wrap pp-text" style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" class="pp-wrap" style="padding:32px 16px;">
      <tr>
        <td align="center">
          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" class="pp-card" style="max-width:560px;border-radius:16px;overflow:hidden;">
            <tr>
              <td style="background-color:#FE6A00;padding:32px 24px;text-align:center;">
                <h1 style="margin:0;font-family:'Bangers',Impact,sans-serif;font-size:42px;letter-spacing:2px;color:#ffffff;line-height:1;">POULE PARTY</h1>
                <p style="margin:12px 0 0;font-family:'Press Start 2P',monospace;font-size:11px;color:#ffffff;letter-spacing:1px;">${escapeHtml(s.headerSubtitle)}</p>
              </td>
            </tr>
            <tr>
              <td class="pp-text" style="padding:32px 28px 16px;">
                <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${s.greeting(reg.playerName)}</p>
                <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${s.body(reg.teamName, reg.teamSize, s.dDay)}</p>
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" class="pp-code-box" style="margin:24px 0;border-radius:12px;">
                  <tr>
                    <td style="padding:20px;text-align:center;">
                      <p class="pp-code-label" style="margin:0 0 8px;font-family:'Press Start 2P',monospace;font-size:10px;letter-spacing:1px;">${escapeHtml(s.codeLabel)}</p>
                      <p style="margin:0;font-family:'Press Start 2P',monospace;font-size:28px;letter-spacing:6px;color:#EF0778;">${escapeHtml(reg.code)}</p>
                    </td>
                  </tr>
                </table>
                <p class="pp-muted" style="margin:0;font-size:14px;line-height:1.55;">${escapeHtml(s.codeInstructions)}</p>
              </td>
            </tr>
            <tr>
              <td class="pp-divider" style="padding:16px 28px 32px;">
                <p class="pp-muted" style="margin:0 0 6px;font-size:13px;">${s.support} <a href="mailto:${SUPPORT_EMAIL}" style="color:#EF0778;text-decoration:none;">${SUPPORT_EMAIL}</a>.</p>
                <p class="pp-faint" style="margin:0;font-size:11px;">${escapeHtml(s.reference)} : ${escapeHtml(reg.registrationId)}</p>
              </td>
            </tr>
          </table>
          <p class="pp-faint" style="margin:16px 0 0;font-size:11px;">${escapeHtml(s.footer)}</p>
        </td>
      </tr>
    </table>
  </body>
</html>`;
}

// Plain-text fallback: strip the HTML markup from the localized strings.
function stripTags(value: string): string {
  return value.replace(/<[^>]+>/g, "").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&");
}

function renderText(reg: RegistrationSnapshot, s: EmailStrings): string {
  return [
    stripTags(s.greeting(reg.playerName)),
    ``,
    stripTags(s.body(reg.teamName, reg.teamSize, s.dDay)),
    ``,
    `${s.codeLabel} : ${reg.code}`,
    ``,
    s.codeInstructions,
    ``,
    `${stripTags(s.support)} ${SUPPORT_EMAIL}.`,
    `${s.reference} : ${reg.registrationId}`,
  ].join("\n");
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export async function sendRegistrationConfirmationEmail(
  reg: RegistrationSnapshot,
  apiKey: string
): Promise<void> {
  const { strings, lang } = pickStrings(reg.locale);
  // Resend's REST API is simpler than the SDK + smaller bundle than
  // adding the `resend` npm package. Same auth, same response shape.
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: FROM_ADDRESS,
      to: [reg.email],
      subject: strings.subject,
      html: renderHtml(reg, strings, lang),
      text: renderText(reg, strings),
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Resend returned ${response.status}: ${body}`);
  }
  logger.info(`Confirmation email (${lang}) sent to ${reg.email} for ${reg.registrationId}`);
}
