import { createContext, useContext, useState, type ReactNode } from "react";
import en from "./en";
import fr from "./fr";
import nl from "./nl";

type Translations = typeof en;
type Locale = "en" | "fr" | "nl";

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

function detectLocale(): Locale {
  const stored = localStorage.getItem("locale") as Locale | null;
  if (stored && translations[stored]) return stored;
  const browser = navigator.language.slice(0, 2);
  if (browser === "fr") return "fr";
  if (browser === "nl") return "nl";
  return "en";
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectLocale);

  const setLocale = (l: Locale) => {
    localStorage.setItem("locale", l);
    document.documentElement.lang = l;
    setLocaleState(l);
  };

  // Set lang on initial render
  if (typeof document !== "undefined") {
    document.documentElement.lang = locale;
  }

  return (
    <I18nContext.Provider value={{ locale, t: translations[locale], setLocale }}>
      {children}
    </I18nContext.Provider>
  );
}

export function useI18n() {
  return useContext(I18nContext);
}
