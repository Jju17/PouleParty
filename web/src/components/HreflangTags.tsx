import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import { LOCALES, equivalentPath, routeKeyFromPath } from "../i18n/routes";

// PP-99 — Injects `<link rel="alternate" hreflang="xx" href="...">`
// tags into `<head>` for the current page. One tag per supported
// locale plus an `x-default` pointing at the English version (Google's
// recommended international fallback).
//
// Implemented as direct DOM mutation rather than via React Helmet so
// we don't add a runtime dep. Re-runs on every pathname change and
// always clears its previous tags first so navigation between routes
// doesn't accumulate stale `<link>` entries.
export default function HreflangTags() {
  const location = useLocation();
  useEffect(() => {
    const origin = window.location.origin;
    const stale = document.querySelectorAll('link[data-pp-hreflang="1"]');
    stale.forEach((el) => el.remove());

    // Unknown route (404 wildcard, transient redirect) — skip injection.
    if (routeKeyFromPath(location.pathname) === null) return;

    LOCALES.forEach((locale) => {
      const link = document.createElement("link");
      link.rel = "alternate";
      link.hreflang = locale;
      link.href = `${origin}${equivalentPath(location.pathname, locale)}`;
      link.setAttribute("data-pp-hreflang", "1");
      document.head.appendChild(link);
    });

    const xDefault = document.createElement("link");
    xDefault.rel = "alternate";
    xDefault.hreflang = "x-default";
    xDefault.href = `${origin}${equivalentPath(location.pathname, "en")}`;
    xDefault.setAttribute("data-pp-hreflang", "1");
    document.head.appendChild(xDefault);
  }, [location.pathname]);
  return null;
}
