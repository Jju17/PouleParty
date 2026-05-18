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

    /** CRIT-9 (audit 2026-05-17) — mid-game route guard. AppNavigation pushes
     *  the current top-of-stack route here; `postValidationCode` drops App Link
     *  payloads while the user is on a gameplay screen so a tap on the email
     *  CTA doesn't yank them out of an active game. iOS does the same drop
     *  inside `AppFeature` for `.chickenMap / .hunterMap / .gameMasterMap /
     *  .victory`. Without this guard, the StateFlow value stays set until the
     *  user returns to Home, where `HomeViewModel.init` collects it and opens
     *  the JoinFlow sheet on top of the post-game screen.
     */
    private val _currentRoute = MutableStateFlow<String?>(null)

    /** Route templates that should reject incoming App Link payloads. Match
     *  the patterns in `navigation.Routes` (the runtime value of
     *  `NavDestination.route` is the template string, not the resolved URL). */
    private val MID_GAME_ROUTE_PREFIXES = listOf(
        "chicken_map/",
        "hunter_map/",
        "game_master_map/",
        "victory/",
    )

    /** Called by AppNavigation on every back-stack change so the bus knows
     *  whether the user is in a gameplay screen. */
    fun updateCurrentRoute(route: String?) {
        _currentRoute.value = route
    }

    /** Push a new code from MainActivity. Empty or whitespace input
     *  is dropped silently so a malformed Intent can't fire an empty
     *  prefill into the JoinFlow. Mid-game routes also drop. */
    fun postValidationCode(code: String) {
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return
        val route = _currentRoute.value
        if (route != null && MID_GAME_ROUTE_PREFIXES.any { route.startsWith(it) }) {
            android.util.Log.d("PP-52-Deeplink", "[DeeplinkBus] dropping during mid-game route=$route")
            return
        }
        _validationCode.value = normalized
    }

    /** Called by the observer once the code has been forwarded into
     *  the JoinFlow, so a later re-emission of the same value (e.g.
     *  after process recreation) reuses a fresh subscription. */
    fun consume() {
        _validationCode.value = null
    }
}
