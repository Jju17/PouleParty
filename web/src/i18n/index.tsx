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

// Safari private mode + some hardened browsers throw on localStorage access.
// Every read/write is wrapped so a storage failure only drops persistence,
// not the whole app.
function safeGetItem(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSetItem(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Ignore: quota exceeded or access denied.
  }
}

function detectLocale(): Locale {
  const stored = safeGetItem("locale") as Locale | null;
  if (stored && translations[stored]) return stored;
  if (typeof navigator !== "undefined") {
    const browser = navigator.language.slice(0, 2);
    if (browser === "fr") return "fr";
    if (browser === "nl") return "nl";
  }
  return "en";
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectLocale);

  const setLocale = (l: Locale) => {
    safeSetItem("locale", l);
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
