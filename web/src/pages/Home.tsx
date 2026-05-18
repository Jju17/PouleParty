import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import ClickableChicken from "../components/ClickableChicken";
import { useI18n } from "../i18n";
import { basePathForLocale } from "./inscriptionPaths";

// PouleParty D-Day registration cutoff. After this instant the Home CTA
// stops rendering so visitors don't land on a form for a past event.
// 2026-06-06 23:59 Europe/Brussels (CEST, UTC+2).
const D_DAY_CTA_CUTOFF = new Date("2026-06-06T23:59:00+02:00");
const D_DAY_BATCH_ID = "game-06-06-2026";

export default function Home() {
  const { t, locale } = useI18n();
  const showDDayCta = Date.now() < D_DAY_CTA_CUTOFF.getTime();
  const inscriptionHref = `${basePathForLocale(locale)}?batchId=${D_DAY_BATCH_ID}`;

  return (
    <Layout>
      <div className="text-center py-16">
        <ClickableChicken className="mb-6 animate-bounce-in" />

        <h1
          className="text-5xl md:text-6xl font-bold mb-4 animate-fade-in-up stagger-1"
          style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.06em", backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text", color: "transparent" }}
        >
          Poule Party
        </h1>

        <p className="text-lg text-black dark:text-gray-200 max-w-lg mx-auto animate-fade-in-up stagger-2">
          {t.home.tagline}
        </p>

        {showDDayCta && (
          <div className="mt-10 animate-fade-in-up stagger-3">
            <Link
              to={inscriptionHref}
              className="block max-w-xl mx-auto rounded-2xl p-6 md:p-8 text-white shadow-lg hover:scale-[1.02] transition-transform duration-300"
              style={{ backgroundImage: "linear-gradient(135deg, #FE6A00, #EF0778)" }}
              aria-label={t.home.dDayCta}
            >
              <p
                className="text-sm tracking-widest uppercase opacity-90 mb-1"
                style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.18em" }}
              >
                {t.home.dDayEyebrow}
              </p>
              <p
                className="text-4xl md:text-5xl mb-3"
                style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
              >
                {t.home.dDayTitle}
              </p>
              <p className="text-base md:text-lg mb-4 opacity-95">{t.home.dDayBody}</p>
              <span
                className="inline-block px-6 py-3 rounded-xl border-2 border-white"
                style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.06em" }}
              >
                {t.home.dDayCta}
              </span>
            </Link>
          </div>
        )}

        <div className="mt-12 animate-fade-in-up stagger-3">
          <p
            className="text-xl text-[#EF0778] dark:text-[#F54D9E] mb-3"
            style={{ fontFamily: "Bangers, cursive" }}
          >
            {t.home.downloadApp}
          </p>
          <div className="flex justify-center items-center gap-4">
            <a
              href="https://apps.apple.com/be/app/poule-party/id6738432103"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:scale-105 transition-transform duration-300"
              aria-label={t.home.appStore}
            >
              <img src="/app-store-badge.svg" alt={t.home.appStore} className="h-[50px]" />
            </a>
            <a
              href="https://play.google.com/store/apps/details?id=dev.rahier.pouleparty2"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:scale-105 transition-transform duration-300"
              aria-label={t.home.googlePlay}
            >
              <img src="/google-play-badge.svg" alt={t.home.googlePlay} className="h-[50px]" />
            </a>
          </div>
          <p className="mt-4 text-sm text-black/70 dark:text-gray-400 max-w-md mx-auto italic">
            {t.home.androidDisclaimer}
          </p>
        </div>
      </div>
    </Layout>
  );
}
