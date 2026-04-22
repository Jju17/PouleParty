import { haversineDistance } from "./powerUpSpawn";

/**
 * Thrown on HTTP statuses where retrying doesn't make sense (auth errors,
 * bad request). Exits the retry loop immediately instead of burning all
 * attempts, matching the behaviour the test suite asserts.
 */
class NonTransientMapboxError extends Error {}

/**
 * Snaps a single coordinate to the nearest walkable road via the Mapbox
 * Directions API.
 *
 * Retries internally on transient failures (5xx, 429, network errors).
 * Throws on persistent failure so the caller (and Cloud Task) can retry the
 * whole batch, silently falling back to the raw coordinate spawned power-ups
 * inside houses / fields / rivers.
 *
 * The 200m-max sanity check returns the original coordinate (not a throw)
 * because a big snap usually means the coord was in a road-free area, not
 * that Mapbox is broken.
 *
 * Extracted into its own module so the retry logic can be unit-tested with a
 * mocked `fetch` without having to initialise firebase-admin.
 */
export async function snapToRoad(
  lat: number,
  lng: number,
  accessToken: string,
  maxRetries = 3,
  backoffMs = (attempt: number) => 500 * 2 ** (attempt - 1),
  sleep: (ms: number) => Promise<void> = (ms) => new Promise((r) => setTimeout(r, ms)),
): Promise<{ latitude: number; longitude: number }> {
  // Second point ~11m north, the Directions API needs 2 points for a route.
  const offsetLat = lat + 0.0001;
  const coordString = `${lng},${lat};${lng},${offsetLat}`;
  const url =
    `https://api.mapbox.com/directions/v5/mapbox/walking/${coordString}` +
    `?access_token=${accessToken}&overview=false&steps=false`;

  let lastError: unknown = null;
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    if (attempt > 0) {
      await sleep(backoffMs(attempt));
    }
    try {
      const resp = await fetch(url);
      if (resp.status === 429 || resp.status >= 500) {
        // Transient error, loop for another attempt.
        lastError = new Error(`Mapbox ${resp.status}`);
        console.warn(`[spawn] snapToRoad ${resp.status} for ${lat},${lng}, attempt ${attempt + 1}/${maxRetries}`);
        continue;
      }
      if (!resp.ok) {
        // Non-transient (4xx, auth, bad request), stop retrying. We throw
        // a tagged error so the local `catch` below can tell us apart from
        // a retryable network/shape error and bail out of the loop.
        throw new NonTransientMapboxError(`Mapbox non-transient ${resp.status}`);
      }
      const json = (await resp.json()) as { waypoints?: Array<{ location: [number, number] }> };
      const waypoint = json.waypoints?.[0];
      if (!waypoint || !Array.isArray(waypoint.location) || waypoint.location.length !== 2) {
        throw new Error("Invalid Mapbox response shape");
      }
      const [snappedLng, snappedLat] = waypoint.location;
      // Reject snaps that moved > 200m, likely a road-free area, not a
      // Mapbox problem. Keeping the raw coord here is fine.
      const distanceMeters = haversineDistance(lat, lng, snappedLat, snappedLng);
      if (distanceMeters > 200) return { latitude: lat, longitude: lng };
      return { latitude: snappedLat, longitude: snappedLng };
    } catch (err) {
      if (err instanceof NonTransientMapboxError) throw err;
      lastError = err;
      console.warn(`[spawn] snapToRoad attempt ${attempt + 1}/${maxRetries} failed for ${lat},${lng}:`, err);
    }
  }
  throw new Error(`snapToRoad exhausted ${maxRetries} retries for ${lat},${lng}: ${String(lastError)}`);
}
