import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";

/**
 * The 3 routes that render this page each pin a specific locale. The
 * mobile apps build the URL from the device language and open it in the
 * browser; the URL slug is therefore the source of truth for which
 * language to display.
 */
const ROUTE_LOCALE_MAP: Record<string, "fr" | "en" | "nl"> = {
  "/creer-une-partie": "fr",
  "/create-a-party": "en",
  "/een-feestje-organiseren": "nl",
};

export default function CreateParty() {
  const { t, setLocale } = useI18n();
  const location = useLocation();

  useEffect(() => {
    const forced = ROUTE_LOCALE_MAP[location.pathname];
    if (forced) setLocale(forced);
  }, [location.pathname, setLocale]);

  return (
    <Layout>
      <h1
        className="text-4xl sm:text-5xl mb-6 leading-none"
        style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
      >
        {t.createParty.title}
      </h1>

      <div className="space-y-6 leading-relaxed">
        <p>{t.createParty.body}</p>

        <div className="space-y-3">
          <p>
            {t.createParty.contactEmailLabel}{" "}
            <a
              href="mailto:julien@rahier.dev"
              className="text-[#FE6A00] underline"
            >
              julien@rahier.dev
            </a>
          </p>
          <p>
            {t.createParty.contactWhatsAppLabel}{" "}
            <a
              href="https://wa.me/32456931516"
              target="_blank"
              rel="noopener noreferrer"
              className="text-[#FE6A00] underline"
            >
              +32 456 93 15 16
            </a>
          </p>
        </div>
      </div>
    </Layout>
  );
}
