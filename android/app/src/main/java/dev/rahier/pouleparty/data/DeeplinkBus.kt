package dev.rahier.pouleparty.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PP-52 — Singleton hub for App Link (`pouleparty.be/join?code=…`)
 * payloads. MainActivity (which has the Intent) writes to it on
 * cold-start + onNewIntent; HomeViewModel observes and dispatches
 * `HomeIntent.DeeplinkValidationCodeReceived(code)` when something
 * shows up so the JoinFlow can pre-fill its validation field.
 *
 * Plain Kotlin `object` rather than a Hilt @Singleton — adding
 * `@Inject lateinit var` to MainActivity triggered a Dagger /
 * kotlin-metadata version mismatch (Hilt's bundled metadata-jvm
 * caps at 2.1.0 while Kotlin emits 2.2.0). A top-level object
 * sidesteps the issue entirely and is just as testable for our
 * needs (one read site, one write site).
 */
object DeeplinkBus {
    private val _validationCode = MutableStateFlow<String?>(null)
    val validationCode: StateFlow<String?> = _validationCode.asStateFlow()

    /** Push a new code from MainActivity. Empty or whitespace input
     *  is dropped silently so a malformed Intent can't fire an empty
     *  prefill into the JoinFlow. */
    fun postValidationCode(code: String) {
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return
        _validationCode.value = normalized
    }

    /** Called by the observer once the code has been forwarded into
     *  the JoinFlow, so a later re-emission of the same value (e.g.
     *  after process recreation) reuses a fresh subscription. */
    fun consume() {
        _validationCode.value = null
    }
}
