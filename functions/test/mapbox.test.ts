import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { snapToRoad } from "../src/mapbox";

// Exercise the retry + fallback logic without hitting Mapbox. Every test
// stubs `fetch` with a queue of responses so we can control exactly what
// the API "returns" on each attempt, and swaps `sleep` out for a no-op
// to keep tests millisecond-fast.

const noSleep = () => Promise.resolve();

function okResponse(snappedLng: number, snappedLat: number): Response {
  return new Response(
    JSON.stringify({ waypoints: [{ location: [snappedLng, snappedLat] }] }),
    { status: 200, headers: { "content-type": "application/json" } },
  );
}

function status(code: number, body: object | string = ""): Response {
  return new Response(typeof body === "string" ? body : JSON.stringify(body), { status: code });
}

type FetchStub = ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe("snapToRoad, happy path", () => {
  test("returns snapped coords on first 200", async () => {
    const fetchStub: FetchStub = vi.fn().mockResolvedValueOnce(okResponse(4.3500123, 50.8500456));
    vi.stubGlobal("fetch", fetchStub);

    const out = await snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep);

    expect(fetchStub).toHaveBeenCalledTimes(1);
    expect(out.latitude).toBeCloseTo(50.8500456, 8);
    expect(out.longitude).toBeCloseTo(4.3500123, 8);
  });
});

describe("snapToRoad, transient failures retry", () => {
  test("429 on first attempt, 200 on second", async () => {
    const fetchStub: FetchStub = vi
      .fn()
      .mockResolvedValueOnce(status(429, "rate limited"))
      .mockResolvedValueOnce(okResponse(4.3501, 50.8501));
    vi.stubGlobal("fetch", fetchStub);

    const out = await snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep);

    expect(fetchStub).toHaveBeenCalledTimes(2);
    expect(out.latitude).toBeCloseTo(50.8501, 8);
  });

  test("500 on first, 503 on second, 200 on third", async () => {
    const fetchStub: FetchStub = vi
      .fn()
      .mockResolvedValueOnce(status(500))
      .mockResolvedValueOnce(status(503))
      .mockResolvedValueOnce(okResponse(4.35, 50.85));
    vi.stubGlobal("fetch", fetchStub);

    const out = await snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep);

    expect(fetchStub).toHaveBeenCalledTimes(3);
    expect(out.latitude).toBeCloseTo(50.85, 8);
  });

  test("network error (fetch rejects) retries", async () => {
    const fetchStub: FetchStub = vi
      .fn()
      .mockRejectedValueOnce(new TypeError("network down"))
      .mockResolvedValueOnce(okResponse(4.35, 50.85));
    vi.stubGlobal("fetch", fetchStub);

    const out = await snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep);

    expect(fetchStub).toHaveBeenCalledTimes(2);
    expect(out.latitude).toBeCloseTo(50.85, 8);
  });

  test("exponential backoff between retries", async () => {
    const fetchStub: FetchStub = vi
      .fn()
      .mockResolvedValueOnce(status(500))
      .mockResolvedValueOnce(status(500))
      .mockResolvedValueOnce(okResponse(4.35, 50.85));
    vi.stubGlobal("fetch", fetchStub);

    const sleeps: number[] = [];
    const sleepSpy = vi.fn((ms: number) => {
      sleeps.push(ms);
      return Promise.resolve();
    });
    await snapToRoad(50.85, 4.35, "TOKEN", 3, (attempt) => 500 * 2 ** (attempt - 1), sleepSpy);

    // Two retries → two sleeps at 500 ms and 1000 ms.
    expect(sleeps).toEqual([500, 1000]);
  });
});

describe("snapToRoad, non-transient failures don't retry", () => {
  test("404 throws immediately (no second attempt)", async () => {
    const fetchStub: FetchStub = vi.fn().mockResolvedValueOnce(status(404));
    vi.stubGlobal("fetch", fetchStub);

    await expect(snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep)).rejects.toThrow(/non-transient 404/);
    expect(fetchStub).toHaveBeenCalledTimes(1);
  });

  test("401 (bad token) throws immediately", async () => {
    const fetchStub: FetchStub = vi.fn().mockResolvedValueOnce(status(401));
    vi.stubGlobal("fetch", fetchStub);

    await expect(snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep)).rejects.toThrow(/non-transient 401/);
    expect(fetchStub).toHaveBeenCalledTimes(1);
  });

  test("malformed JSON body throws, retries treat it as transient", async () => {
    // First response is 200 but body shape is wrong → `throw` inside the try.
    // The catch path treats it as a retryable error (we can't tell if it's
    // a flaky response or a deterministic protocol change); second try
    // returns a good response.
    const fetchStub: FetchStub = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ waypoints: [] }), { status: 200 }))
      .mockResolvedValueOnce(okResponse(4.35, 50.85));
    vi.stubGlobal("fetch", fetchStub);

    const out = await snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep);
    expect(fetchStub).toHaveBeenCalledTimes(2);
    expect(out.latitude).toBeCloseTo(50.85, 8);
  });
});

describe("snapToRoad, exhausted retries", () => {
  test("3 consecutive 500s → throws with exhausted message", async () => {
    const fetchStub: FetchStub = vi
      .fn()
      .mockResolvedValueOnce(status(500))
      .mockResolvedValueOnce(status(500))
      .mockResolvedValueOnce(status(500));
    vi.stubGlobal("fetch", fetchStub);

    await expect(snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep)).rejects.toThrow(/exhausted 3 retries/);
    expect(fetchStub).toHaveBeenCalledTimes(3);
  });

  test("1 retry cap still throws if it fails once", async () => {
    const fetchStub: FetchStub = vi.fn().mockResolvedValueOnce(status(500));
    vi.stubGlobal("fetch", fetchStub);

    await expect(snapToRoad(50.85, 4.35, "TOKEN", 1, () => 0, noSleep)).rejects.toThrow(/exhausted 1 retries/);
    expect(fetchStub).toHaveBeenCalledTimes(1);
  });
});

describe("snapToRoad, 200m sanity check", () => {
  test("snap moved > 200m → returns original coord (no throw)", async () => {
    // 0.01° lat shift ≈ 1.1 km, way beyond the 200m threshold.
    const fetchStub: FetchStub = vi.fn().mockResolvedValueOnce(okResponse(4.35, 50.86));
    vi.stubGlobal("fetch", fetchStub);

    const out = await snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep);

    expect(out.latitude).toBe(50.85); // unchanged
    expect(out.longitude).toBe(4.35); // unchanged
    expect(fetchStub).toHaveBeenCalledTimes(1);
  });

  test("snap within 200m → returns snapped coord", async () => {
    // ~0.0005° lat ≈ 55m, well under the threshold.
    const fetchStub: FetchStub = vi.fn().mockResolvedValueOnce(okResponse(4.35, 50.8505));
    vi.stubGlobal("fetch", fetchStub);

    const out = await snapToRoad(50.85, 4.35, "TOKEN", 3, () => 0, noSleep);

    expect(out.latitude).toBeCloseTo(50.8505, 8);
  });
});

describe("snapToRoad, URL construction", () => {
  test("includes token, coords and correct profile", async () => {
    const fetchStub: FetchStub = vi.fn().mockResolvedValueOnce(okResponse(4.35, 50.85));
    vi.stubGlobal("fetch", fetchStub);

    await snapToRoad(50.85, 4.35, "my-secret-token", 3, () => 0, noSleep);

    const calledUrl = fetchStub.mock.calls[0][0] as string;
    expect(calledUrl).toContain("api.mapbox.com/directions/v5/mapbox/walking");
    expect(calledUrl).toContain("access_token=my-secret-token");
    expect(calledUrl).toContain("4.35,50.85");
    // Second point is offset north by ~0.0001° to satisfy the 2-points-required
    // constraint, protects against a refactor that drops it.
    expect(calledUrl).toContain("4.35,50.8501");
  });
});
