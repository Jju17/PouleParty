import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { I18nProvider } from "./i18n";
import "./index.css";
import Home from "./pages/Home";
import Privacy from "./pages/Privacy";
import Support from "./pages/Support";
import Terms from "./pages/Terms";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <I18nProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/terms" element={<Terms />} />
          <Route path="/support" element={<Support />} />
        </Routes>
      </BrowserRouter>
    </I18nProvider>
  </React.StrictMode>
);
