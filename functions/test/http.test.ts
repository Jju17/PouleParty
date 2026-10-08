import { describe, expect, it, vi } from "vitest";
import { fetchWithRetry } from "../src/http";

const noSleep = () => Promise.resolve();

describe("fetchWithRetry", () => {
  it("retries 429 and 5xx then returns the success", async () => {
    const fetchImpl = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response("", { status: 429 }))
      .mockResolvedValueOnce(new Response("", { status: 503 }))
      .mockResolvedValueOnce(new Response("ok", { status: 200 }));
    const res = await fetchWithRetry("https://x", {}, { fetchImpl, sleep: noSleep });
    expect(res.status).toBe(200);
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });

  it("does not retry a client error", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(new Response("", { status: 400 }));
    const res = await fetchWithRetry("https://x", {}, { fetchImpl, sleep: noSleep });
    expect(res.status).toBe(400);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it("returns the last retryable response once attempts run out", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(new Response("", { status: 500 }));
    const res = await fetchWithRetry("https://x", {}, { fetchImpl, sleep: noSleep, attempts: 2 });
    expect(res.status).toBe(500);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it("rethrows a persistent network error", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockRejectedValue(new Error("offline"));
    await expect(fetchWithRetry("https://x", {}, { fetchImpl, sleep: noSleep })).rejects.toThrow("offline");
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });
});
