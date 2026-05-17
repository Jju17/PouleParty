import { useEffect } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";
import { basePathForLocale, localeFromPathname } from "./inscriptionPaths";

// PP-52 — Stripe cancel_url lands here when the user backs out of the
// Checkout page. The pending registration doc stays around with
// `paid: false` and will simply never get an email; no cleanup needed.

export default function InscriptionCancel() {
  const { t, locale, setLocale } = useI18n();
  const c = t.inscription.cancel;
  const location = useLocation();
  const [searchParams] = useSearchParams();
  // Preserve the batchId so the "retry" link drops them back on
  // the same event registration flow.
  const batchId = searchParams.get("batchId") ?? "";

  useEffect(() => {
    setLocale(localeFromPathname(location.pathname));
  }, [location.pathname, setLocale]);

  const retryHref = `${basePathForLocale(locale)}?batchId=${encodeURIComponent(batchId)}`;

  return (
    <Layout>
      <div className="max-w-md mx-auto text-center py-12 animate-step-forward">
        <h1
          className="text-5xl mb-6 leading-none whitespace-pre-line"
          style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
        >
          {c.title}
        </h1>
        <p className="text-base leading-relaxed mb-6">{c.body}</p>
        {batchId && (
          <Link
            to={retryHref}
            className="inline-block px-6 py-3 rounded-full font-bold tracking-wider bg-[#EF0778] text-white hover:scale-105 transition-transform"
            style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.06em" }}
          >
            {c.retryButton}
          </Link>
        )}
      </div>
    </Layout>
  );
}
