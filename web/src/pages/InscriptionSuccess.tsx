import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import confetti from "canvas-confetti";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";

// PP-52 — Stripe success_url lands here after a confirmed payment.
// The confirmation email (Resend) is the source of truth for the
// validation code; this page is the festive "you're in!" celebration.
// PP-99: locale comes from the URL prefix via `<I18nProvider>`.

// PouleParty brand palette — confetti picks from these so the burst
// reads as "the chicken won" rather than a generic shower.
const BRAND_COLORS = ["#FE6A00", "#EF0778", "#FFD166", "#06D6A0", "#118AB2"];

function fireCelebration() {
  // Initial center burst (the "yay" moment).
  confetti({
    particleCount: 120,
    spread: 80,
    startVelocity: 45,
    origin: { y: 0.4 },
    colors: BRAND_COLORS,
  });
  // Side cannons 250ms later — the staggered timing feels handcrafted
  // rather than a single anonymous splash.
  setTimeout(() => {
    confetti({
      particleCount: 60,
      angle: 60,
      spread: 55,
      startVelocity: 50,
      origin: { x: 0, y: 0.6 },
      colors: BRAND_COLORS,
    });
    confetti({
      particleCount: 60,
      angle: 120,
      spread: 55,
      startVelocity: 50,
      origin: { x: 1, y: 0.6 },
      colors: BRAND_COLORS,
    });
  }, 250);
  // Slow trickle 1s in — keeps the page alive while the user reads
  // the email instructions.
  setTimeout(() => {
    confetti({
      particleCount: 80,
      spread: 100,
      startVelocity: 30,
      gravity: 0.6,
      ticks: 200,
      origin: { y: 0.35 },
      colors: BRAND_COLORS,
    });
  }, 1000);
}

export default function InscriptionSuccess() {
  const { t } = useI18n();
  const [searchParams] = useSearchParams();
  const s = t.inscription.success;
  // Stripe always appends `?session_id={CHECKOUT_SESSION_ID}` on
  // redirect; absence = the user hit the URL directly (no celebration).
  const sessionId = searchParams.get("session_id")?.trim() ?? "";

  useEffect(() => {
    if (!sessionId) return;
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;
    fireCelebration();
  }, [sessionId]);

  if (!sessionId) {
    return (
      <Layout>
        <div className="max-w-md mx-auto text-center py-16">
          <div className="text-5xl mb-4">🐔</div>
          <h1
            className="text-4xl mb-4 leading-none"
            style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
          >
            {s.noSessionTitle}
          </h1>
          <p className="text-base leading-relaxed">{s.noSessionBody}</p>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="max-w-md mx-auto text-center py-12 animate-step-forward relative">
        <div className="text-7xl mb-4 animate-bounce-in inline-block animate-float">
          🐔
        </div>
        <h1
          className="text-5xl mb-2 leading-none whitespace-pre-line animate-fade-in-up stagger-1"
          style={{
            fontFamily: "Bangers, cursive",
            letterSpacing: "0.04em",
            backgroundImage: "linear-gradient(90deg, #FE6A00, #EF0778, #FFD166, #FE6A00)",
            backgroundSize: "200% 200%",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent",
            backgroundClip: "text",
            color: "transparent",
            animation:
              "fade-in-up 0.6s ease-out both 0.1s, gradient-shift 3s ease infinite 0.7s",
          }}
        >
          {s.title}
        </h1>
        <div className="text-3xl mb-6 animate-fade-in-up stagger-2">🎉 🎊 ✨</div>

        <p className="text-base leading-relaxed mb-4 animate-fade-in-up stagger-3">
          {s.line1}
        </p>
        <p className="text-base leading-relaxed mb-4 animate-fade-in-up stagger-4">
          {s.line2}
        </p>
        <p className="text-sm opacity-75 animate-fade-in-up stagger-5">
          {s.spamHint}{" "}
          <a
            href="mailto:julien@rahier.dev"
            className="text-[#EF0778] underline"
          >
            julien@rahier.dev
          </a>
          .
        </p>
        {/* Session id surfaced as a support reference for users whose
            confirmation email never arrived (Resend failure case). */}
        <p className="mt-6 text-xs opacity-60 break-all font-mono">
          {s.referenceLabel}: {sessionId}
        </p>
        <p
          className="text-3xl mt-8 text-[#FE6A00] animate-fade-in-up stagger-6"
          style={{ fontFamily: "Bangers, cursive" }}
        >
          {s.cluck}
        </p>
      </div>
    </Layout>
  );
}
