import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import { LOCALES, equivalentPath, routeKeyFromPath } from "../i18n/routes";

export default function HreflangTags() {
  const location = useLocation();
  useEffect(() => {
    const origin = window.location.origin;
    const stale = document.querySelectorAll('link[data-pp-hreflang="1"]');
    stale.forEach((el) => el.remove());

    // Unknown route (404 wildcard, transient redirect), skip injection.
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
