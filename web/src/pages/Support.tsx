import Layout from "../components/Layout";
import { useI18n } from "../i18n";

export default function Support() {
  const { t } = useI18n();

  return (
    <Layout>
      <h1 className="text-3xl font-bold mb-8">{t.support.title}</h1>

      <div className="space-y-6 text-gray-700 leading-relaxed">
        <section>
          <h2 className="text-xl font-semibold mb-2">{t.support.needHelp}</h2>
          <p>{t.support.needHelpText}</p>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.support.commonIssues}</h2>
          <div className="space-y-4">
            <div>
              <h3 className="font-medium">{t.support.locationTitle}</h3>
              <p className="text-sm text-gray-600">{t.support.locationText}</p>
            </div>
            <div>
              <h3 className="font-medium">{t.support.joinTitle}</h3>
              <p className="text-sm text-gray-600">{t.support.joinText}</p>
            </div>
            <div>
              <h3 className="font-medium">{t.support.mapTitle}</h3>
              <p className="text-sm text-gray-600">{t.support.mapText}</p>
            </div>
          </div>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">{t.support.contactTitle}</h2>
          <p>
            {t.support.contactText}{" "}
            <a href="mailto:julien@rahier.dev" className="text-[#FE6A00] underline">
              julien@rahier.dev
            </a>.
          </p>
          <p className="mt-2">{t.support.contactFooter}</p>
        </section>
      </div>
    </Layout>
  );
}
