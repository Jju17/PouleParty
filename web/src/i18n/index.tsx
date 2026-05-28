import { createContext, useContext, useEffect, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import en from "./en";
import fr from "./fr";
import nl from "./nl";
import { detectLocale, equivalentPath, localeFromPath, type Locale } from "./routes";

type Translations = typeof en;

const translations: Record<Locale, Translations> = { en, fr, nl };

interface I18nContextType {
  locale: Locale;
  t: Translations;
  setLocale: (locale: Locale) => void;
}

const I18nContext = createContext<I18nContextType>({
  locale: "en",
  t: en,
  setLocale: () => {},
});

// Safari private mode + some hardened browsers throw on localStorage
// access. Reads happen inside `detectLocale` (also guarded); writes
// stay best-effort here.
function safeSetItem(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Quota exceeded / access denied — fail silently.
  }
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  // The URL is the source of truth for locale once the visitor is on
  // a `/<locale>/...` route. `<LocaleRedirect />` mounts on `/` and
  // redirects to the detected locale before this provider's children
  // ever render unprefixed paths, so the fallback below only fires on
  // the catch-all wildcard (e.g. typos).
  const urlLocale = localeFromPath(location.pathname);
  const locale: Locale = urlLocale ?? detectLocale();

  useEffect(() => {
    document.documentElement.lang = locale;
    safeSetItem("locale", locale);
  }, [locale]);

  const setLocale = (newLocale: Locale) => {
    if (newLocale === locale) return;
    safeSetItem("locale", newLocale);
    navigate(equivalentPath(location.pathname, newLocale), { replace: true });
  };

  return (
    <I18nContext.Provider value={{ locale, t: translations[locale], setLocale }}>
      {children}
    </I18nContext.Provider>
  );
}

export function useI18n() {
  return useContext(I18nContext);
}

export type { Locale } from "./routes";
