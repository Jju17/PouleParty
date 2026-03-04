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
          <p className="mt-2 font-medium">{t.privacy.controllerDetails}</p>
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
          <h2 className="text-xl font-semibold mb-2">{t.privacy.legalBasis}</h2>
          <p className="mb-3">{t.privacy.legalBasisIntro}</p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>{t.privacy.legalBasisConsent}</strong> {t.privacy.legalBasisConsentText}</li>
            <li><strong>{t.privacy.legalBasisLegitimate}</strong> {t.privacy.legalBasisLegitimateText}</li>
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
          <h2 className="text-xl font-semibold mb-2">{t.privacy.thirdParties}</h2>
          <p className="mb-3">{t.privacy.thirdPartiesIntro}</p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <a href={t.privacy.thirdPartyFirebaseAnalyticsUrl} target="_blank" rel="noopener noreferrer" className="text-[#FE6A00] underline">
                {t.privacy.thirdPartyFirebaseAnalytics}
              </a>
            </li>
            <li>
              <a href={t.privacy.thirdPartyCrashlyticsUrl} target="_blank" rel="noopener noreferrer" className="text-[#FE6A00] underline">
                {t.privacy.thirdPartyCrashlytics}
              </a>
            </li>
            <li>
              <a href={t.privacy.thirdPartyMapboxUrl} target="_blank" rel="noopener noreferrer" className="text-[#FE6A00] underline">
                {t.privacy.thirdPartyMapbox}
              </a>
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.dataSharing}</h2>
          <p>{t.privacy.dataSharingText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.internationalTransfers}</h2>
          <p>{t.privacy.internationalTransfersText}</p>
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
          <p className="mb-3">{t.privacy.rightsIntro}</p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>{t.privacy.rightAccess}</strong> {t.privacy.rightAccessText}</li>
            <li><strong>{t.privacy.rightRectification}</strong> {t.privacy.rightRectificationText}</li>
            <li><strong>{t.privacy.rightErasure}</strong> {t.privacy.rightErasureText}</li>
            <li><strong>{t.privacy.rightRestriction}</strong> {t.privacy.rightRestrictionText}</li>
            <li><strong>{t.privacy.rightPortability}</strong> {t.privacy.rightPortabilityText}</li>
            <li><strong>{t.privacy.rightObject}</strong> {t.privacy.rightObjectText}</li>
            <li><strong>{t.privacy.rightWithdraw}</strong> {t.privacy.rightWithdrawText}</li>
          </ul>
          <p className="mt-3">{t.privacy.rightsExercise}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.privacy.supervisory}</h2>
          <p>{t.privacy.supervisoryText}</p>
          <p className="mt-2">
            <a href={t.privacy.supervisoryUrl} target="_blank" rel="noopener noreferrer" className="text-[#FE6A00] underline">
              {t.privacy.supervisoryAuthority}
            </a>
          </p>
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
