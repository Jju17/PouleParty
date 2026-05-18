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
          <h2 className="text-xl font-semibold mb-2">{t.terms.parties}</h2>
          <p>{t.terms.partiesText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.paidEvents}</h2>
          <p className="mb-3">{t.terms.paidEventsPrice}</p>
          <p className="mb-3">{t.terms.paidEventsWhatsIncluded}</p>
          <p className="mb-3">{t.terms.paidEventsWithdrawal}</p>
          <p className="mb-3">{t.terms.paidEventsRefund}</p>
          <p>{t.terms.paidEventsForceMajeure}</p>
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
          <h2 className="text-xl font-semibold mb-2">{t.terms.ugc}</h2>
          <p>{t.terms.ugcText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.safety}</h2>
          <p>{t.terms.safetyText}</p>
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
          <h2 className="text-xl font-semibold mb-2">{t.terms.governingLaw}</h2>
          <p>{t.terms.governingLawText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.terms.odr}</h2>
          <p>
            {t.terms.odrText}{" "}
            <a href={t.terms.odrUrl} target="_blank" rel="noopener noreferrer" className="text-[#FE6A00] underline">
              {t.terms.odrUrl}
            </a>
          </p>
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
