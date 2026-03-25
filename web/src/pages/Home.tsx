import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import ClickableChicken from "../components/ClickableChicken";
import { useI18n } from "../i18n";

export default function Home() {
  const { t } = useI18n();

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

        <div className="mt-10 animate-fade-in-up stagger-3">
          <Link
            to="/register"
            className="inline-block px-8 py-4 rounded-full text-white font-bold text-lg animate-pulse-glow hover:scale-105 hover:shadow-xl transition-all duration-300"
            style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.08em", backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)" }}
          >
            {t.home.cta}
          </Link>
        </div>

        <div className="mt-12 animate-fade-in-up stagger-4">
          <p
            className="text-xl text-[#EF0778] dark:text-[#F54D9E] mb-3"
            style={{ fontFamily: "Bangers, cursive" }}
          >
            {t.home.appComingSoon}
          </p>
          <div className="flex justify-center items-center gap-4 blur-[3px] opacity-50 pointer-events-none select-none" aria-hidden="true">
            <img src="/app-store-badge.svg" alt="" className="h-[50px]" />
            <img src="/google-play-badge.svg" alt="" className="h-[50px]" />
          </div>
        </div>
      </div>
    </Layout>
  );
}
