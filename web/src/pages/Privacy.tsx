import Layout from "../components/Layout";
import { useI18n } from "../i18n";

export default function Privacy() {
  const { t } = useI18n();

  return (
    <Layout>
      <h1 className="text-3xl font-bold mb-8">{t.privacy.title}</h1>
      <p className="text-sm text-gray-500 mb-8">{t.privacy.lastUpdated}</p>

      <div className="space-y-6 text-gray-700 leading-relaxed">
        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.overview}</h2>
          <p>{t.privacy.overviewText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.dataCollected}</h2>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>{t.privacy.locationData}</strong> {t.privacy.locationDataText}</li>
            <li><strong>{t.privacy.auth}</strong> {t.privacy.authText}</li>
            <li><strong>{t.privacy.playerName}</strong> {t.privacy.playerNameText}</li>
            <li><strong>{t.privacy.analytics}</strong> {t.privacy.analyticsText}</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.howWeUse}</h2>
          <ul className="list-disc pl-6 space-y-2">
            <li>{t.privacy.howWeUse1}</li>
            <li>{t.privacy.howWeUse2}</li>
            <li>{t.privacy.howWeUse3}</li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.dataSharing}</h2>
          <p>{t.privacy.dataSharingText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.dataRetention}</h2>
          <p>{t.privacy.dataRetentionText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.children}</h2>
          <p>{t.privacy.childrenText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.rights}</h2>
          <p>{t.privacy.rightsText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.contact}</h2>
          <p>
            {t.privacy.contactText}{" "}
            <a href="mailto:julien@rahier.dev" className="text-[#FE6A00] underline">
              julien@rahier.dev
            </a>.
          </p>
        </section>
      </div>
    </Layout>
  );
}
