import { Navigate, useLocation } from "react-router-dom";
import { detectLocale } from "../i18n/routes";

// PP-99 — Root `/` and any pathname that doesn't carry a `/<locale>/`
// prefix hit this component. We resolve the locale once (localStorage
// > navigator > `en`) and replace the URL with `/<locale>` so the
// rest of the app can assume the URL is always the source of truth.
//
// `replace: true` so the user's back button doesn't bring them right
// back to `/`. The tradeoff is that a shared `pouleparty.be` link
// always lands on the visitor's own preferred locale, not the
// sender's — acceptable for a public landing site.
export default function LocaleRedirect() {
  const location = useLocation();
  const target = `/${detectLocale()}${location.search}${location.hash}`;
  return <Navigate replace to={target} />;
}
