export interface RetryOptions {
  attempts?: number;
  timeoutMs?: number;
  backoffMs?: (attempt: number) => number;
  sleep?: (ms: number) => Promise<void>;
  fetchImpl?: typeof fetch;
}

function isRetryableStatus(status: number): boolean {
  return status === 429 || status >= 500;
}

/**
 * `fetch` with a per-attempt timeout and retries on 429, 5xx and network
 * errors. Returns the last response so callers keep their own status checks.
 */
export async function fetchWithRetry(
  url: string,
  init: RequestInit,
  options: RetryOptions = {}
): Promise<Response> {
  const attempts = options.attempts ?? 3;
  const timeoutMs = options.timeoutMs ?? 10_000;
  const backoffMs = options.backoffMs ?? ((attempt) => 500 * 2 ** attempt);
  const sleep = options.sleep ?? ((ms) => new Promise<void>((resolve) => setTimeout(resolve, ms)));
  const fetchImpl = options.fetchImpl ?? fetch;

  let lastError: unknown = null;
  for (let attempt = 0; attempt < attempts; attempt++) {
    if (attempt > 0) await sleep(backoffMs(attempt - 1));
    try {
      const response = await fetchImpl(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
      if (!isRetryableStatus(response.status) || attempt === attempts - 1) return response;
      lastError = new Error(`HTTP ${response.status}`);
    } catch (err) {
      lastError = err;
    }
  }
  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}
