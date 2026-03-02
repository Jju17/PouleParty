import { createContext, useContext, useState, type ReactNode } from "react";
import en from "./en";
import fr from "./fr";

type Translations = typeof en;
type Locale = "en" | "fr";

const translations: Record<Locale, Translations> = { en, fr };

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
  return browser === "fr" ? "fr" : "en";
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectLocale);

  const setLocale = (l: Locale) => {
    localStorage.setItem("locale", l);
    setLocaleState(l);
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
