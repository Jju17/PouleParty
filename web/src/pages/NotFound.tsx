import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";
import { routePath } from "../i18n/routes";

// PP-99 — 404 page mounted on the React Router wildcard route.
// The locale comes from `<I18nProvider>` (URL prefix when present,
// else detected). We don't try to write a 404 HTTP status — Firebase
// hosting serves 200 + index.html for every unmatched path; the
// status is cosmetic for crawlers and they'll figure it out from
// the page content + Open Graph.
export default function NotFound() {
  const { t, locale } = useI18n();
  const n = t.notFound;
  return (
    <Layout>
      <div className="max-w-xl mx-auto text-center py-12 animate-step-forward">
        <div className="text-7xl mb-4 animate-float inline-block" aria-hidden="true">
          🐔
        </div>
        <p
          className="text-xs tracking-[0.3em] uppercase text-[#EF0778] mb-3"
          style={{ fontFamily: "'Press Start 2P', monospace", letterSpacing: "0.3em" }}
        >
          {n.eyebrow}
        </p>
        <h1
          className="text-5xl sm:text-6xl mb-6 leading-none whitespace-pre-line"
          style={{
            fontFamily: "Bangers, cursive",
            letterSpacing: "0.04em",
            backgroundImage: "linear-gradient(90deg, #FE6A00, #EF0778)",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent",
            backgroundClip: "text",
            color: "transparent",
          }}
        >
          {n.title}
        </h1>
        <p className="text-base sm:text-lg leading-relaxed mb-8 max-w-md mx-auto opacity-90">
          {n.body}
        </p>
        <Link
          to={routePath("home", locale)}
          className="inline-block px-7 py-3 rounded-full font-bold tracking-wider text-white hover:scale-105 transition-transform shadow-lg"
          style={{
            fontFamily: "Bangers, cursive",
            letterSpacing: "0.08em",
            backgroundImage: "linear-gradient(135deg, #FE6A00, #EF0778)",
          }}
        >
          {n.cta}
        </Link>
      </div>
    </Layout>
  );
}
