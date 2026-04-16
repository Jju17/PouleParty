package dev.rahier.pouleparty.ui.onboarding

/** User-initiated actions on the onboarding flow. */
sealed interface OnboardingIntent {
    object NextPage : OnboardingIntent
    object PreviousPage : OnboardingIntent
    object DismissLocationAlert : OnboardingIntent
    object DismissProfanityAlert : OnboardingIntent
    object RefreshPermissions : OnboardingIntent
    object RefreshNotificationPermission : OnboardingIntent
    object OnboardingCompletedLogged : OnboardingIntent
    data class PageSet(val page: Int) : OnboardingIntent
    data class NicknameChanged(val name: String) : OnboardingIntent
}
