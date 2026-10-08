import { Link, useSearchParams } from "react-router-dom";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";
import { routePath } from "../i18n/routes";
import { isAllowedBatchId } from "../registrationBatches";

export default function InscriptionCancel() {
  const { t, locale } = useI18n();
  const c = t.inscription.cancel;
  const [searchParams] = useSearchParams();
  const requestedBatchId = searchParams.get("batchId") ?? "";
  const batchId = isAllowedBatchId(requestedBatchId) ? requestedBatchId : "";

  const retryHref = `${routePath("inscription", locale)}?batchId=${encodeURIComponent(batchId)}`;

  return (
    <Layout>
      <meta name="robots" content="noindex" />
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
