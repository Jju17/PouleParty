import { describe, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import axe from "axe-core";
import { I18nProvider } from "../i18n";
import en from "../i18n/en";
import DeleteAccount from "./DeleteAccount";

vi.mock("../appCheck", () => ({ getAppCheckToken: vi.fn(async () => "token") }));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/en/delete-account"]}>
      <I18nProvider>
        <DeleteAccount />
      </I18nProvider>
    </MemoryRouter>
  );
}

describe("DeleteAccount", () => {
  test("flags an invalid email without calling the server", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText(en.deleteAccount.formEmailLabel), "not-an-email{Enter}");
    expect(await screen.findByRole("alert")).toHaveTextContent(en.deleteAccount.formErrorInvalidEmail);
    expect(screen.getByLabelText(en.deleteAccount.formEmailLabel)).toHaveAttribute("aria-invalid", "true");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test("submits with the keyboard and confirms the request", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText(en.deleteAccount.formEmailLabel), "Me@Example.com{Enter}");
    expect(await screen.findByRole("status")).toBeInTheDocument();
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body)).email).toBe("me@example.com");
    expect((init.headers as Record<string, string>)["X-Firebase-AppCheck"]).toBe("token");
  });

  test("explains a failed browser verification instead of the server message", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "Missing App Check token" }), { status: 401 })));
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText(en.deleteAccount.formEmailLabel), "me@example.com{Enter}");
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(en.deleteAccount.formErrorVerification);
    expect(alert).not.toHaveTextContent("Missing App Check token");
  });

  test("has no detectable accessibility violations", async () => {
    const { container } = renderPage();
    const results = await axe.run(container, { rules: { "color-contrast": { enabled: false } } });
    expect(results.violations.map((v) => v.id)).toEqual([]);
  });
});
