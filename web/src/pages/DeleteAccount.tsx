import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";

const SUPPORT_EMAIL = "julien@rahier.dev";

export default function DeleteAccount() {
  const { t } = useI18n();
  const mailto = `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(
    "Delete my Poule Party account"
  )}`;

  return (
    <Layout>
      <h1 className="text-3xl font-bold mb-6">{t.deleteAccount.title}</h1>

      <div className="space-y-6 text-black dark:text-gray-300 leading-relaxed">
        <p>{t.deleteAccount.intro}</p>

        <section>
          <h2 className="text-xl font-semibold mb-2">
            {t.deleteAccount.dataDeletedTitle}
          </h2>
          <ul className="list-disc pl-6 space-y-1">
            {t.deleteAccount.dataDeleted.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">
            {t.deleteAccount.dataKeptTitle}
          </h2>
          <ul className="list-disc pl-6 space-y-1">
            {t.deleteAccount.dataKept.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">
            {t.deleteAccount.howTitle}
          </h2>
          <p className="mb-3">{t.deleteAccount.howText}</p>
          <ul className="list-disc pl-6 space-y-1 mb-4">
            {t.deleteAccount.howList.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <p className="mb-6">{t.deleteAccount.timeframe}</p>

          <a
            href={mailto}
            className="inline-block px-5 py-3 rounded-full text-white font-bold shadow-md hover:shadow-lg hover:scale-[1.02] transition-all duration-300"
            style={{
              backgroundImage:
                "linear-gradient(to right, #FE6A00, #EF0778)",
            }}
          >
            {t.deleteAccount.emailButton}
          </a>
          <p className="mt-3 text-sm">
            <a
              href={mailto}
              className="text-[#FE6A00] underline break-all"
            >
              {SUPPORT_EMAIL}
            </a>
          </p>
        </section>

        <p>
          <Link to="/" className="text-[#FE6A00] underline">
            {t.deleteAccount.backHome}
          </Link>
        </p>
      </div>
    </Layout>
  );
}
