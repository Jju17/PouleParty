import { useState } from "react";
import { httpsCallable, type HttpsCallableResult } from "firebase/functions";
import { functions } from "../firebase";
import Layout from "../components/Layout";
import { useI18n } from "../i18n";

type FormData = {
  firstName: string;
  lastName: string;
  email: string;
  willingToPay: string;
};

const registerForEvent = httpsCallable(functions, "registerForEvent");

export default function Register() {
  const { t } = useI18n();
  const r = t.register;

  const [form, setForm] = useState<FormData>({ firstName: "", lastName: "", email: "", willingToPay: "" });
  const [status, setStatus] = useState<"idle" | "submitting" | "success" | "duplicate" | "full" | "error">("idle");

  const update = (field: keyof FormData) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatus("submitting");
    try {
      await registerForEvent({
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        willingToPay: form.willingToPay,
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

  if (status === "success") {
    return (
      <Layout>
        <div className="text-center py-16">
          <div className="text-6xl mb-4">🐔</div>
          <h1
            className="text-3xl font-bold mb-4 text-[#FE6A00]"
            style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
          >
            {r.successTitle}
          </h1>
          <p className="text-gray-600 max-w-md mx-auto">{r.successText}</p>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      {/* Hero */}
      <div className="text-center mb-10">
        <img src="/chicken-logo.png" alt="Poule Party" className="h-24 mx-auto mb-4" />
        <h1
          className="text-4xl font-bold mb-2 text-[#FE6A00]"
          style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
        >
          Poule Party
        </h1>
        <p className="text-sm text-gray-500">{r.date}</p>
      </div>

      {/* Description */}
      <div className="space-y-6 text-gray-700 leading-relaxed mb-10">
        <p className="text-lg font-medium">{r.intro}</p>
        <p>{r.description}</p>

        <div>
          <h2
            className="text-xl font-bold mb-2 text-[#FE6A00]"
            style={{ fontFamily: "Bangers, cursive" }}
          >
            {r.conceptTitle}
          </h2>
          <p>{r.conceptText}</p>
          <p className="mt-2 font-medium">{r.mission}</p>
          <p className="mt-2">{r.zoneText}</p>
          <p className="mt-2 font-medium">{r.butWait}</p>
        </div>

        <div>
          <h2
            className="text-xl font-bold mb-2 text-[#FE6A00]"
            style={{ fontFamily: "Bangers, cursive" }}
          >
            {r.pointsTitle}
          </h2>
          <ul className="space-y-1">
            {r.pointsList.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
          <p className="mt-2">{r.pointsOutro}</p>
        </div>

        <div>
          <h2
            className="text-xl font-bold mb-2 text-[#FE6A00]"
            style={{ fontFamily: "Bangers, cursive" }}
          >
            {r.whyTitle}
          </h2>
          <ul className="space-y-1 list-disc list-inside">
            {r.whyList.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>

        <div>
          <h2
            className="text-xl font-bold mb-2 text-[#FE6A00]"
            style={{ fontFamily: "Bangers, cursive" }}
          >
            {r.infoTitle}
          </h2>
          <ul className="space-y-1">
            {r.infoList.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      </div>

      {/* Registration form */}
      <div className="bg-white rounded-2xl shadow-md p-8 mb-10">
        <h2
          className="text-2xl font-bold mb-6 text-center text-[#FE6A00]"
          style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.04em" }}
        >
          {r.formTitle}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-5 max-w-md mx-auto">
          <div>
            <label htmlFor="firstName" className="block text-sm font-medium mb-1">
              {r.firstNameLabel}
            </label>
            <input
              id="firstName"
              type="text"
              required
              value={form.firstName}
              onChange={update("firstName")}
              placeholder={r.firstNamePlaceholder}
              className="w-full rounded-lg border border-gray-300 px-4 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#FE6A00] focus:border-transparent"
            />
          </div>

          <div>
            <label htmlFor="lastName" className="block text-sm font-medium mb-1">
              {r.lastNameLabel}
            </label>
            <input
              id="lastName"
              type="text"
              required
              value={form.lastName}
              onChange={update("lastName")}
              placeholder={r.lastNamePlaceholder}
              className="w-full rounded-lg border border-gray-300 px-4 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#FE6A00] focus:border-transparent"
            />
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium mb-1">
              {r.emailLabel}
            </label>
            <input
              id="email"
              type="email"
              required
              value={form.email}
              onChange={update("email")}
              placeholder={r.emailPlaceholder}
              className="w-full rounded-lg border border-gray-300 px-4 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#FE6A00] focus:border-transparent"
            />
          </div>

          <div>
            <label htmlFor="willingToPay" className="block text-sm font-medium mb-1">
              {r.willingToPayLabel}
            </label>
            <input
              id="willingToPay"
              type="text"
              required
              value={form.willingToPay}
              onChange={update("willingToPay")}
              placeholder={r.willingToPayPlaceholder}
              className="w-full rounded-lg border border-gray-300 px-4 py-2.5 focus:outline-none focus:ring-2 focus:ring-[#FE6A00] focus:border-transparent"
            />
          </div>

          {status === "duplicate" && (
            <p className="text-red-500 text-sm">{r.duplicateText}</p>
          )}
          {status === "full" && (
            <p className="text-red-500 text-sm">{r.fullText}</p>
          )}
          {status === "error" && (
            <p className="text-red-500 text-sm">{r.errorText}</p>
          )}

          <button
            type="submit"
            disabled={status === "submitting"}
            className="w-full bg-[#FE6A00] text-white font-bold py-3 rounded-lg hover:bg-[#e55e00] transition-colors disabled:opacity-50"
            style={{ fontFamily: "Bangers, cursive", letterSpacing: "0.06em", fontSize: "1.1rem" }}
          >
            {status === "submitting" ? r.submitting : r.submitButton}
          </button>
        </form>
      </div>
    </Layout>
  );
}
