import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { I18nProvider } from "./i18n";
import { ThemeProvider } from "./theme";
import ErrorBoundary from "./components/ErrorBoundary";
import "./index.css";
import Home from "./pages/Home";
import Privacy from "./pages/Privacy";
import Support from "./pages/Support";
import Terms from "./pages/Terms";
import DeleteAccount from "./pages/DeleteAccount";
import CreateParty from "./pages/CreateParty";

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
        <I18nProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/privacy" element={<Privacy />} />
              <Route path="/terms" element={<Terms />} />
              <Route path="/support" element={<Support />} />
              <Route path="/delete-account" element={<DeleteAccount />} />
              {/* PP-46: localized "create a party" landing pages. The mobile
                  apps pick the URL based on device language; this page also
                  pins the i18n locale via the URL slug regardless of the
                  visitor's stored preference. */}
              <Route path="/creer-une-partie" element={<CreateParty />} />
              <Route path="/create-a-party" element={<CreateParty />} />
              <Route path="/een-feestje-organiseren" element={<CreateParty />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </I18nProvider>
      </ThemeProvider>
    </ErrorBoundary>
  </React.StrictMode>
);
