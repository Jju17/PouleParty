import { Navigate, useLocation } from "react-router-dom";
import { detectLocale } from "../i18n/routes";

export default function LocaleRedirect() {
  const location = useLocation();
  const target = `/${detectLocale()}${location.search}${location.hash}`;
  return <Navigate replace to={target} />;
}
