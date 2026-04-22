package dev.rahier.pouleparty.ui.paymentconfirmation

/**
 * Pure formatting / time helpers for the payment-confirmation screen. Kept
 * module-internal so unit tests can cover the awkward edge cases
 * (negative remaining, > 1 day, hour boundary, very large values).
 */
internal object PaymentConfirmationLogic {

    /**
     * Formats a non-negative `secondsRemaining` into the compact time string
     * the confirmation card displays.
     *
     * Rules:
     * - 0 → `"—"` (no countdown)
     * - < 1 h → `"m:ss"`
     * - < 1 d → `"h:mm:ss"`
     * - ≥ 1 d → `"Dj hh:mm:ss"`
     *
     * Callers must clamp negatives before calling. We don't do it here because
     * a silent clamp would hide invariant breaks from the tests.
     */
    fun formatCountdown(secondsRemaining: Long): String {
        require(secondsRemaining >= 0) { "secondsRemaining must be non-negative, was $secondsRemaining" }
        if (secondsRemaining == 0L) return "—"
        val days = secondsRemaining / 86_400
        val hours = (secondsRemaining % 86_400) / 3_600
        val minutes = (secondsRemaining % 3_600) / 60
        val secs = secondsRemaining % 60
        return when {
            days > 0 -> "%dj %02d:%02d:%02d".format(days, hours, minutes, secs)
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, secs)
            else -> "%d:%02d".format(minutes, secs)
        }
    }

    /**
     * Clamp the raw `startMs - nowMs` delta into a non-negative number of
     * seconds. If the start time is in the past (clock skew, or user lingered
     * past game-start on the confirmation screen), we show zero rather than
     * a negative countdown.
     */
    fun secondsRemainingUntil(startMs: Long, nowMs: Long): Long {
        val diffMs = startMs - nowMs
        if (diffMs <= 0) return 0L
        return diffMs / 1_000L
    }
}
