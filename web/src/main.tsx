import React, { type ComponentType } from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { I18nProvider } from "./i18n";
import { ThemeProvider } from "./theme";
import ErrorBoundary from "./components/ErrorBoundary";
import LocaleRedirect from "./components/LocaleRedirect";
import HreflangTags from "./components/HreflangTags";
import { initAppCheck } from "./appCheck";
import { LOCALES, ROUTES, type RouteKey } from "./i18n/routes";
import "./index.css";

// CRIT-4 (audit 2026-05-17): boot App Check before render so the SDK has
// time to fetch a token in the background while the user navigates. Token
// fetch is async; the Inscription form awaits the token when it submits.
initAppCheck();
import Home from "./pages/Home";
import Privacy from "./pages/Privacy";
import Support from "./pages/Support";
import Terms from "./pages/Terms";
import DeleteAccount from "./pages/DeleteAccount";
import CreateParty from "./pages/CreateParty";
import Inscription from "./pages/Inscription";
import InscriptionSuccess from "./pages/InscriptionSuccess";
import InscriptionCancel from "./pages/InscriptionCancel";
import NotFound from "./pages/NotFound";

const PAGE_COMPONENTS: Record<RouteKey, ComponentType> = {
  home: Home,
  inscription: Inscription,
  inscriptionSuccess: InscriptionSuccess,
  inscriptionCancel: InscriptionCancel,
  createParty: CreateParty,
  privacy: Privacy,
  terms: Terms,
  support: Support,
  deleteAccount: DeleteAccount,
};

// PP-99 — Build the `<Route>` list by cross-producting locales × route
// keys. Each (locale, key) pair yields exactly one path. Unknown
// `/<locale>/<slug>` combos (e.g. `/fr/registration`) intentionally
// don't match anything and fall through to the wildcard 301 to
// `/<detected-locale>`.
function buildLocalizedRoutes() {
  return LOCALES.flatMap((locale) =>
    (Object.keys(ROUTES) as RouteKey[]).map((key) => {
      const slug = ROUTES[key][locale];
      const path = slug === "" ? `/${locale}` : `/${locale}/${slug}`;
      const Component = PAGE_COMPONENTS[key];
      return <Route key={`${locale}-${key}`} path={path} element={<Component />} />;
    })
  );
}

const rootElement = document.getElementById("root");
if (!rootElement) {
  // Without a root mount the app can't render; surface a readable message
  // instead of dying on `null!` so infra errors are at least diagnosable.
  document.body.innerHTML =
    '<pre style="padding:24px;font-family:system-ui">Poule Party failed to start: missing #root element.</pre>';
  throw new Error("Missing #root element in index.html");
}

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <ErrorBoundary>
      <ThemeProvider>
        <BrowserRouter>
          <I18nProvider>
            <HreflangTags />
            <Routes>
              {buildLocalizedRoutes()}
              <Route path="/" element={<LocaleRedirect />} />
              <Route path="*" element={<NotFound />} />
            </Routes>
          </I18nProvider>
        </BrowserRouter>
      </ThemeProvider>
    </ErrorBoundary>
  </React.StrictMode>
);
