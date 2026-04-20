import { useState, useEffect } from "react";
import { httpsCallable, type HttpsCallableResult } from "firebase/functions";
import { functions } from "../firebase";
import Layout from "../components/Layout";
import ClickableChicken from "../components/ClickableChicken";
import { useI18n } from "../i18n";

type FormData = {
  firstName: string;
  lastName: string;
  email: string;
  gsm: string;
  willingToPay: string;
  comment: string;
};

const registerForEvent = httpsCallable(functions, "registerForEvent");
const getRegistrationCount = httpsCallable<void, { count: number; max: number }>(functions, "getRegistrationCount");

export default function Register() {
  const { t } = useI18n();
  const r = t.register;

  const [form, setForm] = useState<FormData>({ firstName: "", lastName: "", email: "", gsm: "", willingToPay: "", comment: "" });
  const [gsmError, setGsmError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState(false);
  const [status, setStatus] = useState<"idle" | "submitting" | "success" | "duplicate" | "full" | "error">("idle");
  const [regInfo, setRegInfo] = useState<{ count: number; max: number } | null>(null);

  useEffect(() => {
    getRegistrationCount()
      .then((res) => setRegInfo(res.data))
      .catch(() => {});
  }, [status]);

  const update = (field: keyof FormData) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const validateGsm = (value: string): string | null => {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const digits = trimmed.replace(/[^\d]/g, "");

    if (trimmed.startsWith("+32") || (!trimmed.startsWith("+") && trimmed.startsWith("0"))) {
      // Belgian: +32 4XX XX XX XX (11 digits) or 04XX XX XX XX (10 digits)
      const expected = trimmed.startsWith("+") ? 11 : 10;
      if (digits.length < expected) return r.gsmTooShort.replace("{0}", String(expected - digits.length));
      if (digits.length > expected) return r.gsmTooLong.replace("{0}", String(digits.length - expected));
      return null;
    }
    if (trimmed.startsWith("+33")) {
      // French: +33 6 XX XX XX XX (11 digits)
      if (digits.length < 11) return r.gsmTooShort.replace("{0}", String(11 - digits.length));
      if (digits.length > 11) return r.gsmTooLong.replace("{0}", String(digits.length - 11));
      return null;
    }
    if (trimmed.startsWith("+")) {
      // Other international: at least 8 digits, max 15
      if (digits.length < 8) return r.gsmTooShort.replace("{0}", String(8 - digits.length));
      if (digits.length > 15) return r.gsmTooLong.replace("{0}", String(digits.length - 15));
      return null;
    }
    return r.gsmFormatError;
  };

  const handleGsmChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setForm((f) => ({ ...f, gsm: value }));
    setGsmError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatus("submitting");
    try {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      const hasEmailError = !emailRegex.test(form.email.trim());
      const gsmErr = validateGsm(form.gsm);
      setEmailError(hasEmailError);
      setGsmError(gsmErr);
      if (hasEmailError || gsmErr) {
        setStatus("idle");
        return;
      }
      await registerForEvent({
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        gsm: form.gsm,
        willingToPay: form.willingToPay,
        comment: form.comment,
      }) as HttpsCallableResult<{ success: boolean }>;
      setStatus("success");
    } catch (err: unknown) {
      const code = (err as { code?: string }).code;
      if (code === "functions/already-exists") {
        setStatus("duplicate");
      } else if (code === "functions/resource-exhausted") {
        setStatus("full");
      } else {
        setStatus("error");
      }
    }
  };

  const card = "bg-[#FFFDF5] dark:bg-white/5 rounded-2xl p-6 shadow-md hover:shadow-xl hover:-translate-y-1 transition-all duration-300 border border-[#F0E6D0] dark:border-transparent";
  const heading = "text-2xl font-bold mb-3";
  const headingStyle = { fontFamily: "Bangers, cursive", letterSpacing: "0.04em", backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text", color: "transparent" } as const;
  const bodyText = "text-black dark:text-gray-200";
  const mutedText = "text-black/80 dark:text-gray-400";

  if (status === "success") {
    return (
      <Layout>
        <div className="text-center py-16">
          <div className="text-7xl mb-6 animate-bounce-in">🐔</div>
          <h1
            className="text-4xl font-bold mb-4 animate-fade-in-up stagger-1"
            style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em", backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text", color: "transparent" }}
          >
            {r.successTitle}
          </h1>
          <p className={`max-w-md mx-auto animate-fade-in-up stagger-2 ${mutedText}`}>{r.successText}</p>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      {/* Hero */}
      <div className="text-center mb-12">
        <ClickableChicken className="mb-5 animate-bounce-in" />
        <h1
          className="text-5xl md:text-6xl font-bold mb-3 animate-fade-in-up stagger-1"
          style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.06em", backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text", color: "transparent" }}
        >
          Poule Party
        </h1>
        <div className="animate-fade-in-up stagger-2">
          <span className="inline-block px-4 py-1.5 rounded-full text-white text-sm font-bold tracking-wide" style={{ backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)" }}>
            {r.date}
          </span>
        </div>
      </div>

      {/* Description cards */}
      <div className="space-y-6 mb-12">
        <div className={`${card} animate-fade-in-up stagger-2`}>
          <p className={`text-lg font-medium ${bodyText}`}>{r.intro}</p>
          <p className={`mt-2 ${mutedText}`}>{r.description}</p>
        </div>

        <div className={`${card} animate-fade-in-up stagger-3`}>
          <h2 className={heading} style={headingStyle}>{r.conceptTitle}</h2>
          <p className={bodyText}>{r.conceptText}</p>
          <p className={`mt-2 font-semibold ${bodyText}`}>{r.mission}</p>
          <p className={`mt-2 ${mutedText}`}>{r.zoneText}</p>
          <p className="mt-3 font-semibold text-[#EF0778] dark:text-[#F54D9E]">{r.butWait}</p>
        </div>

        <div className={`${card} animate-fade-in-up stagger-4`}>
          <h2 className={heading} style={headingStyle}>{r.pointsTitle}</h2>
          <ul className="space-y-2">
            {r.pointsList.map((item, i) => (
              <li key={i} className={`text-lg ${bodyText}`}>{item}</li>
            ))}
          </ul>
          <p className={`mt-3 font-medium ${mutedText}`}>{r.pointsOutro}</p>
        </div>

        <div className="grid md:grid-cols-2 gap-6">
          <div className={`${card} animate-fade-in-up stagger-5`}>
            <h2 className={heading} style={headingStyle}>{r.whyTitle}</h2>
            <ul className="space-y-2">
              {r.whyList.map((item, i) => (
                <li key={i} className={`flex items-start gap-2 ${bodyText}`}>
                  <span className="text-[#FE6A00] dark:text-[#FF8C33] mt-0.5">&#9656;</span> {item}
                </li>
              ))}
            </ul>
          </div>

          <div className={`${card} animate-fade-in-up stagger-6`}>
            <h2 className={heading} style={headingStyle}>{r.infoTitle}</h2>
            <ul className="space-y-2">
              {r.infoList.map((item, i) => (
                <li key={i} className={`text-lg ${bodyText}`}>{item}</li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      {/* Registration form */}
      <div className="relative bg-[#FFFDF5] dark:bg-white/5 rounded-2xl shadow-xl p-8 mb-10 animate-fade-in-up stagger-6 overflow-hidden border border-[#F0E6D0] dark:border-transparent">
        <div className="absolute top-0 left-0 right-0 h-1.5 animate-gradient" style={{ backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778, #FE6A00)" }} />

        <h2
          className="text-3xl font-bold mb-2 text-center"
          style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.06em", backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text", color: "transparent" }}
        >
          {r.formTitle}
        </h2>

        {regInfo !== null && (
          <p className={`text-center text-sm mb-6 ${mutedText} opacity-60`}>
            {r.spotsLeft.replace("{0}", String(regInfo.count)).replace("{1}", String(regInfo.max))}
          </p>
        )}

        <form onSubmit={handleSubmit} className="space-y-5 max-w-md mx-auto">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="firstName" className={`block text-sm font-medium mb-1.5 ${mutedText}`}>
                {r.firstNameLabel}
              </label>
              <input
                id="firstName"
                type="text"
                required
                value={form.firstName}
                onChange={update("firstName")}
                placeholder={r.firstNamePlaceholder}
                className="w-full rounded-xl border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-white/10 text-black dark:text-white px-4 py-3 focus:outline-none focus:border-[#FE6A00] focus:ring-3 focus:ring-[#FE6A00]/20 transition-all duration-300 placeholder-gray-400 dark:placeholder-gray-500"
              />
            </div>
            <div>
              <label htmlFor="lastName" className={`block text-sm font-medium mb-1.5 ${mutedText}`}>
                {r.lastNameLabel}
              </label>
              <input
                id="lastName"
                type="text"
                required
                value={form.lastName}
                onChange={update("lastName")}
                placeholder={r.lastNamePlaceholder}
                className="w-full rounded-xl border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-white/10 text-black dark:text-white px-4 py-3 focus:outline-none focus:border-[#FE6A00] focus:ring-3 focus:ring-[#FE6A00]/20 transition-all duration-300 placeholder-gray-400 dark:placeholder-gray-500"
              />
            </div>
          </div>

          <div>
            <label htmlFor="email" className={`block text-sm font-medium mb-1.5 ${mutedText}`}>
              {r.emailLabel}
            </label>
            <p className={`text-xs mb-1.5 ${mutedText} opacity-70`}>{r.emailHint}</p>
            <input
              id="email"
              type="email"
              required
              value={form.email}
              onChange={update("email")}
              placeholder={r.emailPlaceholder}
              className={`w-full rounded-xl border-2 ${emailError ? "border-[#C41E45]" : "border-gray-200 dark:border-gray-700"} bg-white dark:bg-white/10 text-black dark:text-white px-4 py-3 focus:outline-none focus:border-[#FE6A00] focus:ring-3 focus:ring-[#FE6A00]/20 transition-all duration-300 placeholder-gray-400 dark:placeholder-gray-500`}
            />
            {emailError && (
              <p className="text-[#C41E45] dark:text-red-300 text-xs mt-1">{r.emailError}</p>
            )}
          </div>

          <div>
            <label htmlFor="gsm" className={`block text-sm font-medium mb-1.5 ${mutedText}`}>
              {r.gsmLabel}
            </label>
            <p className={`text-xs mb-1.5 ${mutedText} opacity-70`}>{r.gsmHint}</p>
            <input
              id="gsm"
              type="tel"
              required
              value={form.gsm}
              onChange={handleGsmChange}
              placeholder={r.gsmPlaceholder}
              className={`w-full rounded-xl border-2 ${gsmError ? "border-[#C41E45]" : "border-gray-200 dark:border-gray-700"} bg-white dark:bg-white/10 text-black dark:text-white px-4 py-3 focus:outline-none focus:border-[#FE6A00] focus:ring-3 focus:ring-[#FE6A00]/20 transition-all duration-300 placeholder-gray-400 dark:placeholder-gray-500`}
            />
            {gsmError && (
              <p className="text-[#C41E45] dark:text-red-300 text-xs mt-1">{gsmError}</p>
            )}
          </div>

          <div>
            <label htmlFor="willingToPay" className={`block text-sm font-medium mb-1.5 ${mutedText}`}>
              {r.willingToPayLabel}
            </label>
            <input
              id="willingToPay"
              type="text"
              required
              value={form.willingToPay}
              onChange={update("willingToPay")}
              placeholder={r.willingToPayPlaceholder}
              className="w-full rounded-xl border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-white/10 text-black dark:text-white px-4 py-3 focus:outline-none focus:border-[#FE6A00] focus:ring-3 focus:ring-[#FE6A00]/20 transition-all duration-300 placeholder-gray-400 dark:placeholder-gray-500"
            />
          </div>

          <div>
            <label htmlFor="comment" className={`block text-sm font-medium mb-1.5 ${mutedText}`}>
              {r.commentLabel}
            </label>
            <p className={`text-xs mb-1.5 ${mutedText} opacity-70`}>{r.commentHint}</p>
            <textarea
              id="comment"
              value={form.comment}
              onChange={(e) => setForm((f) => ({ ...f, comment: e.target.value }))}
              placeholder={r.commentPlaceholder}
              rows={3}
              className="w-full rounded-xl border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-white/10 text-black dark:text-white px-4 py-3 focus:outline-none focus:border-[#FE6A00] focus:ring-3 focus:ring-[#FE6A00]/20 transition-all duration-300 placeholder-gray-400 dark:placeholder-gray-500 resize-none"
            />
          </div>

          {status === "duplicate" && (
            <p className="text-[#C41E45] dark:text-red-300 text-sm font-medium bg-red-50 dark:bg-red-900/20 rounded-lg px-4 py-2">{r.duplicateText}</p>
          )}
          {status === "full" && (
            <p className="text-[#C41E45] dark:text-red-300 text-sm font-medium bg-red-50 dark:bg-red-900/20 rounded-lg px-4 py-2">{r.fullText}</p>
          )}
          {status === "error" && (
            <p className="text-[#C41E45] dark:text-red-300 text-sm font-medium bg-red-50 dark:bg-red-900/20 rounded-lg px-4 py-2">{r.errorText}</p>
          )}

          <button
            type="submit"
            disabled={status === "submitting"}
            className="w-full py-4 rounded-xl text-white font-bold hover:scale-[1.02] hover:shadow-xl active:scale-[0.98] transition-all duration-300 disabled:opacity-50 disabled:hover:scale-100"
            style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.08em", fontSize: "1.2rem", backgroundImage: "linear-gradient(to right, #FE6A00, #EF0778)" }}
          >
            {status === "submitting" ? r.submitting : r.submitButton}
          </button>
        </form>
      </div>
    </Layout>
  );
}
