import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";
import { getAppCheckToken } from "../appCheck";

// AND-H6 (store-audit 2026-05-18) — self-service deletion form. Replaces
// the previous mailto-only fallback that Google Play 2024+ rejects at
// upload. Posts to `/api/processAccountDeletion` (Firebase Hosting rewrite
// → CF in europe-west1). App Check token attached so reCAPTCHA Enterprise
// gates the endpoint the same way the inscription form does.

const SUPPORT_EMAIL = "julien@rahier.dev";
const API_ENDPOINT = "/api/processAccountDeletion";

// Same shape as the server-side validator in `accountDeletion.ts`. Re-used
// client-side so typos surface inline before the round-trip.
const EMAIL_RE = /^[^\s@,]+@[^\s@,]+\.[^\s@,]+$/;

type Status = "idle" | "submitting" | "success" | "error";

export default function DeleteAccount() {
  const { t } = useI18n();
  const mailto = `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(
    "Delete my Poule Party account"
  )}`;

  const [email, setEmail] = useState("");
  const [nickname, setNickname] = useState("");
  const [reason, setReason] = useState("");
  // CRIT-4 mirror : honeypot. Real users never see this field; bots that
  // auto-fill every visible input populate it → server rejects.
  const [nicknameAlt, setNicknameAlt] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (status === "submitting") return;
    const trimmedEmail = email.trim().toLowerCase();
    if (!EMAIL_RE.test(trimmedEmail)) {
      setStatus("error");
      setErrorMessage(t.deleteAccount.formErrorInvalidEmail);
      return;
    }
    setStatus("submitting");
    setErrorMessage(null);
    try {
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
      };
      const token = await getAppCheckToken();
      if (token) headers["X-Firebase-AppCheck"] = token;
      const response = await fetch(API_ENDPOINT, {
        method: "POST",
        headers,
        body: JSON.stringify({
          email: trimmedEmail,
          nickname: nickname.trim() || undefined,
          reason: reason.trim() || undefined,
          nicknameAlt,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.error ?? `HTTP ${response.status}`);
      }
      setStatus("success");
    } catch (err) {
      setStatus("error");
      setErrorMessage(
        err instanceof Error && err.message
          ? err.message
          : t.deleteAccount.formErrorGeneric
      );
    }
  }

  const submitting = status === "submitting";

  return (
    <Layout>
      <h1 className="text-3xl font-bold mb-6">{t.deleteAccount.title}</h1>

      <div className="space-y-6 text-black dark:text-gray-300 leading-relaxed">
        <p>{t.deleteAccount.intro}</p>

        <section>
          <h2 className="text-xl font-semibold mb-2">
            {t.deleteAccount.dataDeletedTitle}
          </h2>
          <ul className="list-disc pl-6 space-y-1">
            {t.deleteAccount.dataDeleted.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">
            {t.deleteAccount.dataKeptTitle}
          </h2>
          <ul className="list-disc pl-6 space-y-1">
            {t.deleteAccount.dataKept.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold mb-2">
            {t.deleteAccount.formTitle}
          </h2>
          <p className="mb-4">{t.deleteAccount.formSubtitle}</p>

          {status === "success" ? (
            <div
              className="rounded-xl p-4 text-white"
              style={{
                backgroundImage:
                  "linear-gradient(to right, #FE6A00, #EF0778)",
              }}
              role="status"
              aria-live="polite"
            >
              {t.deleteAccount.formSuccess.replace("{email}", email.trim().toLowerCase())}
            </div>
          ) : (
            <form onSubmit={onSubmit} noValidate className="space-y-4">
              <div>
                <label
                  htmlFor="del-email"
                  className="block text-sm font-medium mb-1"
                >
                  {t.deleteAccount.formEmailLabel}
                </label>
                <input
                  id="del-email"
                  type="email"
                  required
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={t.deleteAccount.formEmailPlaceholder}
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-black dark:text-white"
                  disabled={submitting}
                />
              </div>

              <div>
                <label
                  htmlFor="del-nickname"
                  className="block text-sm font-medium mb-1"
                >
                  {t.deleteAccount.formNicknameLabel}
                </label>
                <input
                  id="del-nickname"
                  type="text"
                  maxLength={60}
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  placeholder={t.deleteAccount.formNicknamePlaceholder}
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-black dark:text-white"
                  disabled={submitting}
                />
              </div>

              <div>
                <label
                  htmlFor="del-reason"
                  className="block text-sm font-medium mb-1"
                >
                  {t.deleteAccount.formReasonLabel}
                </label>
                <textarea
                  id="del-reason"
                  rows={3}
                  maxLength={500}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder={t.deleteAccount.formReasonPlaceholder}
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-black dark:text-white"
                  disabled={submitting}
                />
              </div>

              {/* Honeypot — hidden from real users, bots auto-fill it. */}
              <div
                aria-hidden="true"
                style={{
                  position: "absolute",
                  left: "-9999px",
                  width: "1px",
                  height: "1px",
                  overflow: "hidden",
                }}
              >
                <label htmlFor="del-nickname-alt">Leave this empty</label>
                <input
                  id="del-nickname-alt"
                  type="text"
                  tabIndex={-1}
                  autoComplete="off"
                  value={nicknameAlt}
                  onChange={(e) => setNicknameAlt(e.target.value)}
                />
              </div>

              {status === "error" && errorMessage && (
                <p
                  className="text-sm text-red-600 dark:text-red-400"
                  role="alert"
                >
                  {errorMessage}
                </p>
              )}

              <button
                type="submit"
                disabled={submitting}
                className="inline-block px-5 py-3 rounded-full text-white font-bold shadow-md hover:shadow-lg hover:scale-[1.02] transition-all duration-300 disabled:opacity-60 disabled:cursor-not-allowed disabled:hover:scale-100"
                style={{
                  backgroundImage:
                    "linear-gradient(to right, #FE6A00, #EF0778)",
                }}
              >
                {submitting
                  ? t.deleteAccount.formSubmitting
                  : t.deleteAccount.formSubmit}
              </button>
            </form>
          )}

          <p className="mt-6 text-sm">{t.deleteAccount.timeframe}</p>

          <p className="mt-4 text-sm text-black/70 dark:text-gray-400">
            {t.deleteAccount.fallbackHint}{" "}
            <a
              href={mailto}
              className="text-[#FE6A00] underline break-all"
            >
              {SUPPORT_EMAIL}
            </a>
          </p>
        </section>

        <p>
          <Link to="/" className="text-[#FE6A00] underline">
            {t.deleteAccount.backHome}
          </Link>
        </p>
      </div>
    </Layout>
  );
}
