import { describe, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import axe from "axe-core";
import { I18nProvider } from "../i18n";
import en from "../i18n/en";
import Inscription from "./Inscription";

vi.mock("../appCheck", () => ({ getAppCheckToken: vi.fn<() => Promise<string | null>>(async () => null) }));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/en/registration?batchId=game-06-06-2026"]}>
      <I18nProvider>
        <Inscription />
      </I18nProvider>
    </MemoryRouter>
  );
}

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  const f = en.inscription.form;
  await user.click(screen.getByRole("button", { name: en.inscription.intro.cta }));
  await user.type(screen.getByLabelText(f.playerNameLabel), "Ada Lovelace");
  await user.type(screen.getByLabelText(f.teamNameLabel), "Les Poulets");
  await user.type(screen.getByLabelText(f.emailLabel), "ada@example.com");
  await user.type(screen.getByLabelText(f.phoneLabel), "+32470000000");
  await user.click(screen.getByRole("button", { name: new RegExp(`^4`) }));
}

describe("Inscription", () => {
  test("marks the selected team size as pressed", async () => {
    const user = userEvent.setup();
    renderPage();
    await fillForm(user);
    expect(screen.getByRole("button", { name: new RegExp(`^4`) })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: new RegExp(`^3`) })).toHaveAttribute("aria-pressed", "false");
  });

  test("shows the localized total and explains a failed verification", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "Missing App Check token" }), { status: 401 })));
    const user = userEvent.setup();
    renderPage();
    await fillForm(user);
    await user.click(screen.getByRole("button", { name: en.inscription.form.next }));
    const pay = screen.getByRole("button", { name: /PAY €48/ });
    await user.click(pay);
    expect(await screen.findByRole("alert")).toHaveTextContent(en.inscription.recap.verificationError);
    expect(screen.queryByText(/Missing App Check token/)).not.toBeInTheDocument();
  });

  test("has no detectable accessibility violations on the form step", async () => {
    const user = userEvent.setup();
    const { container } = renderPage();
    await user.click(screen.getByRole("button", { name: en.inscription.intro.cta }));
    const results = await axe.run(container, { rules: { "color-contrast": { enabled: false } } });
    expect(results.violations.map((v) => v.id)).toEqual([]);
  });
});
