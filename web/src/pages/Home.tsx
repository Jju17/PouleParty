import Layout from "../components/Layout";
import { useI18n } from "../i18n";

export default function Home() {
  const { t } = useI18n();

  return (
    <Layout>
      <div className="text-center py-16">
        <img src="/chicken-logo.png" alt="Poule Party" className="h-32 mx-auto mb-6" />
        <h1 className="text-4xl font-bold mb-4" style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}>Poule Party</h1>
        <p className="text-lg text-gray-600 max-w-md mx-auto">
          {t.home.tagline}
        </p>
        <div className="mt-8 flex flex-col items-center gap-3">
          <p
            className="text-2xl text-[#FE6A00]"
            style={{ fontFamily: "Bangers, cursive" }}
          >
            Coming soon...
          </p>
          <div className="flex justify-center items-center gap-4 blur-[3px] opacity-60 pointer-events-none select-none">
            <img
              src="/app-store-badge.svg"
              alt=""
              className="h-[50px]"
            />
            <img
              src="/google-play-badge.svg"
              alt=""
              className="h-[50px]"
            />
          </div>
        </div>
      </div>
    </Layout>
  );
}
