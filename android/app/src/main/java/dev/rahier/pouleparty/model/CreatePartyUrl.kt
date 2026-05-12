package dev.rahier.pouleparty.model

/**
 * Builds the localized "create a party" landing-page URL so the Home
 * CTA can route the user to the right page (PP-46). Parity with the iOS
 * `CreatePartyURL` helper — the slugs are the same ones the web routes
 * are registered under.
 */
object CreatePartyUrl {
    /**
     * Map an ISO 639-1 language code (`Locale.getDefault().language`)
     * to the matching path segment. Anything outside fr / nl falls back
     * to the English route.
     */
    fun forLanguage(languageCode: String?): String {
        val path = when (languageCode) {
            "fr" -> "creer-une-partie"
            "nl" -> "een-feestje-organiseren"
            else -> "create-a-party"
        }
        return "https://pouleparty.be/$path"
    }
}
