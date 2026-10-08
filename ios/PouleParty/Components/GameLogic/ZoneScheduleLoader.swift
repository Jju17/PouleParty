import Foundation

/// The stored zone schedule, retried with a short backoff before giving up.
func loadZoneSchedule(
    _ gameId: String,
    fetch: (String) async throws -> [ZoneCircle],
    sleep: (Duration) async throws -> Void,
    attempts: Int = 3
) async -> Result<[ZoneCircle], Error> {
    var lastError: Error = ApiError(code: .unknown)
    for attempt in 0..<max(attempts, 1) {
        do {
            return .success(try await fetch(gameId))
        } catch {
            lastError = error
            guard attempt < attempts - 1 else { break }
            do { try await sleep(.seconds(1 << attempt)) } catch { return .failure(error) }
        }
    }
    return .failure(lastError)
}
