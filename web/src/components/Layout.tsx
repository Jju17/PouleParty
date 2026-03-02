import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useI18n } from "../i18n";

export default function Layout({ children }: { children: ReactNode }) {
  const { locale, t, setLocale } = useI18n();

  return (
    <div className="min-h-screen bg-[#FFF8E7] text-gray-900 flex flex-col">
      <header className="p-6 flex justify-between items-center max-w-3xl mx-auto w-full">
        <Link to="/" className="text-2xl font-bold tracking-wide" style={{ fontFamily: "Bangers, cursive" }}>
          Poule Party
        </Link>
        <nav className="flex items-center gap-4 text-sm">
          <Link to="/privacy" className="hover:text-[#FE6A00] transition-colors">
            {t.nav.privacy}
          </Link>
          <Link to="/support" className="hover:text-[#FE6A00] transition-colors">
            {t.nav.support}
          </Link>
          <button
            onClick={() => setLocale(locale === "en" ? "fr" : "en")}
            className="ml-2 px-2 py-1 rounded border border-gray-300 text-xs font-medium hover:bg-gray-100 transition-colors"
          >
            {locale === "en" ? "FR" : "EN"}
          </button>
        </nav>
      </header>
      <main className="flex-1 max-w-3xl mx-auto w-full px-6 py-8">
        {children}
      </main>
      <footer className="p-6 text-center text-sm text-gray-400">
        &copy; {new Date().getFullYear()} Julien Rahier. {t.footer.rights}
      </footer>
    </div>
  );
}
