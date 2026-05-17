import { useEffect, useMemo, useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";
import { localeFromPathname } from "./inscriptionPaths";

// PP-52 — 3-step inscription wizard (intro → form → récap → Stripe).
// All copy comes from `t.inscription.*` (en/fr/nl). The page pins
// its locale based on the URL slug (`/inscription` = FR,
// `/registration` = EN, `/inschrijving` = NL), same convention as
// PP-46's "create a party" page. The header toggle still works
// in-session if a visitor wants to switch.

const API_ENDPOINT = "/api/createPendingRegistration";
const UNIT_PRICE_EUR = 12;
const TEAM_SIZES = [3, 4, 5] as const;
type TeamSize = (typeof TEAM_SIZES)[number];

interface FormState {
  playerName: string;
  teamName: string;
  email: string;
  phone: string;
  teamSize: TeamSize | null;
}

const EMPTY_FORM: FormState = {
  playerName: "",
  teamName: "",
  email: "",
  phone: "",
  teamSize: null,
};

function isFormValid(form: FormState): form is FormState & { teamSize: TeamSize } {
  if (form.teamSize === null) return false;
  if (!form.playerName.trim()) return false;
  if (!form.teamName.trim()) return false;
  if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) return false;
  if (form.phone.trim().length < 6) return false;
  return true;
}

export default function Inscription() {
  const { t, locale, setLocale } = useI18n();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const batchId = searchParams.get("batchId")?.trim() ?? "";

  // Pin the locale to the URL slug on mount; the header toggle still
  // lets the visitor flip languages in-session.
  useEffect(() => {
    setLocale(localeFromPathname(location.pathname));
  }, [location.pathname, setLocale]);

  const [step, setStep] = useState<1 | 2 | 3>(1);
  // Tracks the last navigation direction so step swaps slide from the
  // matching edge (forward = enter-from-right, back = enter-from-left).
  const [direction, setDirection] = useState<"forward" | "backward">("forward");
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const total = useMemo(
    () => (form.teamSize ? form.teamSize * UNIT_PRICE_EUR : 0),
    [form.teamSize]
  );

  if (!batchId) {
    return (
      <Layout>
        <FatalError
          title={t.inscription.fatalError.title}
          body={t.inscription.fatalError.body}
        />
      </Layout>
    );
  }

  const goTo = (target: 1 | 2 | 3) => {
    setDirection(target > step ? "forward" : "backward");
    setStep(target);
  };

  async function submit() {
    if (!isFormValid(form)) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await fetch(API_ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          batchId,
          playerName: form.playerName.trim(),
          teamName: form.teamName.trim(),
          email: form.email.trim().toLowerCase(),
          phone: form.phone.trim(),
          teamSize: form.teamSize,
          locale,
        }),
      });
      const json = (await response.json()) as { checkoutUrl?: string; error?: string };
      if (!response.ok || !json.checkoutUrl) {
        throw new Error(json.error ?? `Error ${response.status}`);
      }
      window.location.href = json.checkoutUrl;
    } catch (err) {
      setError((err as Error).message || t.inscription.recap.defaultError);
      setSubmitting(false);
    }
  }

  const animationClass =
    direction === "forward" ? "animate-step-forward" : "animate-step-backward";

  return (
    <Layout>
      <div className="max-w-xl mx-auto">
        {step !== 1 && <StepChip current={step - 1} total={2} />}

        <div key={step} className={animationClass}>
          {step === 1 && <IntroStep t={t} onNext={() => goTo(2)} />}
          {step === 2 && (
            <FormStep
              t={t}
              form={form}
              onChange={setForm}
              onBack={() => goTo(1)}
              onNext={() => goTo(3)}
            />
          )}
          {step === 3 && (
            <RecapStep
              t={t}
              form={form}
              total={total}
              submitting={submitting}
              error={error}
              onBack={() => goTo(2)}
              onPay={submit}
            />
          )}
        </div>
      </div>
    </Layout>
  );
}

type T = ReturnType<typeof useI18n>["t"];

function StepChip({ current, total }: { current: number; total: number }) {
  return (
    <div className="mb-4 inline-block px-3 py-1 rounded-full text-xs font-bold tracking-widest bg-[#FE6A00]/10 text-[#FE6A00]">
      {String(current).padStart(2, "0")} / {String(total).padStart(2, "0")}
    </div>
  );
}

function IntroStep({ t, onNext }: { t: T; onNext: () => void }) {
  const { intro } = t.inscription;
  return (
    <div className="text-center py-6">
      <div className="text-5xl mb-4">🐔</div>
      <p className="text-xs tracking-[0.2em] uppercase text-[#EF0778] mb-2">
        {intro.eyebrow}
      </p>
      <h1
        className="text-5xl sm:text-6xl mb-6 leading-none whitespace-pre-line"
        style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
      >
        {intro.title}
      </h1>
      <div className="text-base leading-relaxed mb-6 space-y-1">
        {intro.body.map((line, i) => (
          <p key={i}>{line}</p>
        ))}
      </div>
      <p className="text-base font-bold text-[#FE6A00] mb-8">{intro.priceLine}</p>
      <NextButton label={intro.cta} onClick={onNext} />
    </div>
  );
}

function FormStep({
  t,
  form,
  onChange,
  onBack,
  onNext,
}: {
  t: T;
  form: FormState;
  onChange: (form: FormState) => void;
  onBack: () => void;
  onNext: () => void;
}) {
  const { form: f } = t.inscription;
  const valid = isFormValid(form);
  return (
    <div>
      <h2
        className="text-4xl mb-2 leading-none"
        style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
      >
        {f.title}
      </h2>
      <p className="text-sm opacity-75 mb-6">{f.subtitle}</p>

      <div className="space-y-4">
        <Field label={f.playerNameLabel}>
          <input
            type="text"
            value={form.playerName}
            onChange={(e) => onChange({ ...form, playerName: e.target.value })}
            placeholder={f.playerNamePlaceholder}
            className={inputClass}
            autoComplete="name"
          />
        </Field>
        <Field label={f.teamNameLabel}>
          <input
            type="text"
            value={form.teamName}
            onChange={(e) => onChange({ ...form, teamName: e.target.value })}
            placeholder={f.teamNamePlaceholder}
            className={inputClass}
            autoComplete="off"
          />
        </Field>
        <Field label={f.emailLabel}>
          <input
            type="email"
            value={form.email}
            onChange={(e) => onChange({ ...form, email: e.target.value })}
            placeholder={f.emailPlaceholder}
            className={inputClass}
            autoComplete="email"
            inputMode="email"
          />
        </Field>
        <Field label={f.phoneLabel}>
          <input
            type="tel"
            value={form.phone}
            onChange={(e) => onChange({ ...form, phone: e.target.value })}
            placeholder={f.phonePlaceholder}
            className={inputClass}
            autoComplete="tel"
            inputMode="tel"
          />
        </Field>
        <Field label={f.teamSizeLabel}>
          <div className="grid grid-cols-3 gap-2">
            {TEAM_SIZES.map((size) => (
              <button
                key={size}
                type="button"
                onClick={() => onChange({ ...form, teamSize: size })}
                className={`py-3 rounded-xl border-2 font-bold transition-all ${
                  form.teamSize === size
                    ? "bg-[#FE6A00] text-white border-[#FE6A00]"
                    : "border-[#FE6A00]/40 hover:border-[#FE6A00] hover:bg-[#FE6A00]/10"
                }`}
              >
                <span className="text-2xl block leading-none">{size}</span>
                <span className="text-[10px] tracking-widest opacity-80">
                  {f.teamSizeUnit}
                </span>
              </button>
            ))}
          </div>
        </Field>
      </div>

      <div className="flex justify-between items-center mt-8">
        <BackButton label={f.back} onClick={onBack} />
        <NextButton label={f.next} onClick={onNext} disabled={!valid} />
      </div>
    </div>
  );
}

function RecapStep({
  t,
  form,
  total,
  submitting,
  error,
  onBack,
  onPay,
}: {
  t: T;
  form: FormState;
  total: number;
  submitting: boolean;
  error: string | null;
  onBack: () => void;
  onPay: () => void;
}) {
  const { recap } = t.inscription;
  const payLabel = submitting
    ? recap.redirecting
    : recap.payButtonTemplate.replace("{total}", String(total));
  return (
    <div>
      <h2
        className="text-4xl mb-2 leading-none"
        style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
      >
        {recap.title}
      </h2>
      <p className="text-sm opacity-75 mb-6">{recap.subtitle}</p>

      <div className="rounded-2xl border-2 border-[#FE6A00]/30 p-5 space-y-3">
        <RecapRow label={recap.captain} value={form.playerName} />
        <RecapRow label={recap.team} value={form.teamName} />
        <RecapRow label={recap.email} value={form.email} small />
        <RecapRow label={recap.phone} value={form.phone} />
        <RecapRow label={recap.players} value={String(form.teamSize ?? "—")} />
        <div className="border-t border-[#FE6A00]/30 pt-3 flex justify-between items-baseline">
          <span className="text-xs tracking-widest uppercase opacity-75">
            {recap.total}
          </span>
          <span
            className="text-3xl text-[#FE6A00]"
            style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
          >
            {total} €
          </span>
        </div>
      </div>

      <p className="text-xs opacity-75 text-center mt-4 leading-relaxed">
        {recap.note}
        <br />
        {recap.paymentSecure}
      </p>

      {error && (
        <div className="mt-4 p-3 rounded-xl bg-red-100 text-red-800 text-sm">
          {error}
        </div>
      )}

      <div className="flex justify-between items-center mt-6">
        <BackButton label={recap.back} onClick={onBack} disabled={submitting} />
        <NextButton label={payLabel} onClick={onPay} disabled={submitting} />
      </div>
    </div>
  );
}

function FatalError({ title, body }: { title: string; body: string }) {
  return (
    <div className="max-w-md mx-auto text-center py-16">
      <h1
        className="text-4xl mb-4 leading-none"
        style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
      >
        {title}
      </h1>
      <p className="text-base leading-relaxed">{body}</p>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-xs tracking-widest uppercase opacity-75 block mb-1.5">
        {label}
      </span>
      {children}
    </label>
  );
}

function RecapRow({
  label,
  value,
  small,
}: {
  label: string;
  value: string;
  small?: boolean;
}) {
  return (
    <div className="flex justify-between items-baseline gap-3">
      <span className="text-xs tracking-widest uppercase opacity-75 shrink-0">
        {label}
      </span>
      <span
        className={`text-right break-all ${small ? "text-xs" : "text-base"}`}
        style={small ? { fontFamily: "monospace", letterSpacing: 0 } : undefined}
      >
        {value || "—"}
      </span>
    </div>
  );
}

const inputClass =
  "w-full px-4 py-3 rounded-xl border-2 border-[#FE6A00]/30 bg-transparent focus:outline-none focus:border-[#FE6A00] transition-colors";

function NextButton({
  label,
  onClick,
  disabled,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`px-6 py-3 rounded-full font-bold tracking-wider transition-all ${
        disabled
          ? "bg-gray-300 text-gray-500 cursor-not-allowed"
          : "bg-[#EF0778] text-white hover:scale-105"
      }`}
      style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.06em" }}
    >
      {label}
    </button>
  );
}

function BackButton({
  label,
  onClick,
  disabled,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`text-sm font-bold tracking-wider opacity-75 hover:opacity-100 ${
        disabled ? "cursor-not-allowed" : ""
      }`}
    >
      {label}
    </button>
  );
}
