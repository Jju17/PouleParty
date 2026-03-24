import Layout from "../components/Layout";
import { useI18n } from "../i18n";

export default function Terms() {
  const { t } = useI18n();

  return (
    <Layout>
      <h1 className="text-3xl font-bold mb-8">{t.terms.title}</h1>
      <p className="text-sm text-black/70 dark:text-gray-400 mb-8">{t.terms.lastUpdated}</p>

      <div className="space-y-6 text-black dark:text-gray-300 leading-relaxed">
        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.acceptance}</h2>
          <p>{t.terms.acceptanceText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.description}</h2>
          <p>{t.terms.descriptionText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.userConduct}</h2>
          <ul className="list-disc pl-6 space-y-2">
            <li>{t.terms.conduct1}</li>
            <li>{t.terms.conduct2}</li>
            <li>{t.terms.conduct3}</li>
            <li>{t.terms.conduct4}</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.location}</h2>
          <p>{t.terms.locationText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.disclaimer}</h2>
          <p>{t.terms.disclaimerText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.liability}</h2>
          <p>{t.terms.liabilityText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.termination}</h2>
          <p>{t.terms.terminationText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.changes}</h2>
          <p>{t.terms.changesText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.contact}</h2>
          <p>
            {t.terms.contactText}{" "}
            <a href="mailto:julien@rahier.dev" className="text-[#FE6A00] underline">
              julien@rahier.dev
            </a>.
          </p>
        </section>
      </div>
    </Layout>
  );
}
