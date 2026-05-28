import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useI18n } from "../i18n";
import { routePath, type Locale } from "../i18n/routes";
import { useTheme } from "../theme";

const NEXT_LOCALE: Record<Locale, Locale> = {
  en: "fr",
  fr: "nl",
  nl: "en",
};

export default function Layout({ children }: { children: ReactNode }) {
  const { locale, t, setLocale } = useI18n();
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === "dark";

  const footerLink = (to: string, label: string) => (
    <Link
      to={to}
      className={`transition-colors duration-200 ${
        isDark ? "hover:text-[#FF8C33]" : "hover:text-[#FE6A00]"
      }`}
    >
      {label}
    </Link>
  );

  return (
    <div
      className="min-h-screen flex flex-col transition-colors duration-500"
      style={{
        background: isDark
          ? "#1A1A2E"
          : "radial-gradient(ellipse at center, #FDF9D5 30%, #FFE8C8 100%)",
        color: isDark ? "#F0F0F0" : "#1A1A2E",
      }}
    >
      {/* Header */}
      <header className="px-4 py-4 sm:px-6 sm:py-6 flex justify-between items-center max-w-4xl mx-auto w-full">
        <Link
          to={routePath("home", locale)}
          className="text-2xl sm:text-3xl font-bold tracking-wide hover:scale-105 transition-transform duration-300 pb-1 shrink-0"
          style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.06em", lineHeight: 1, backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text", color: "transparent" }}
        >
          Poule Party
        </Link>
        <nav className="flex items-center gap-2">
          {/* PP-99 — Locale toggle. `setLocale` navigates to the
              equivalent path in the new locale, so `/fr/inscription`
              becomes `/nl/inschrijving` rather than dropping the user
              on `/nl` (the home of the new locale). */}
          <button
            onClick={() => setLocale(NEXT_LOCALE[locale])}
            className={`px-2.5 py-1.5 rounded-full border-2 text-xs font-bold transition-all duration-300 ${
              isDark
                ? "border-[#FF8C33] text-[#FF8C33] hover:bg-[#FF8C33] hover:text-[#1A1A2E]"
                : "border-[#FE6A00] text-[#FE6A00] hover:bg-[#FE6A00] hover:text-white"
            }`}
            aria-label={`Switch to ${NEXT_LOCALE[locale].toUpperCase()}`}
          >
            {NEXT_LOCALE[locale].toUpperCase()}
          </button>
        </nav>
      </header>

      {/* Main */}
      <main className="flex-1 max-w-4xl mx-auto w-full px-4 sm:px-6 py-8">
        {children}
      </main>

      {/* Footer */}
      <footer className={`p-6 text-center text-sm ${isDark ? "text-gray-400" : "text-gray-600"}`}>
        <div className="flex justify-center gap-4 mb-2">
          {footerLink(routePath("privacy", locale), t.nav.privacy)}
          <span>·</span>
          {footerLink(routePath("terms", locale), t.nav.terms)}
          <span>·</span>
          {footerLink(routePath("support", locale), t.nav.support)}
        </div>
        &copy; {new Date().getFullYear()} Julien Rahier. {t.footer.rights}
      </footer>

      {/* Theme toggle — bottom right corner */}
      <button
        onClick={toggleTheme}
        className={`fixed bottom-5 right-5 w-10 h-10 rounded-full flex items-center justify-center text-lg transition-all duration-300 hover:scale-110 shadow-lg ${
          isDark
            ? "bg-[#16213E] border border-gray-700 hover:border-[#FF8C33]"
            : "bg-white/80 border border-gray-200 hover:border-[#FE6A00]"
        }`}
        aria-label="Toggle dark mode"
        tabIndex={0}
      >
        {isDark ? "☀️" : "🌙"}
      </button>
    </div>
  );
}
