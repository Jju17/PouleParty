import Layout from "../components/Layout";
import { useI18n } from "../i18n";

// PP-99 — Locale is derived from the URL prefix (`/fr/...`, `/en/...`,
// `/nl/...`) by `<I18nProvider>`. No per-page pinning needed.
export default function CreateParty() {
  const { t } = useI18n();

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
