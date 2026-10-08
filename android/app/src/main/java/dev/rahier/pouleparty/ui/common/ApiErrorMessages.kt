package dev.rahier.pouleparty.ui.common

import androidx.annotation.StringRes
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.data.ApiErrorCode
import dev.rahier.pouleparty.data.toApiErrorCode

@StringRes
fun ApiErrorCode.messageRes(): Int = when (this) {
    ApiErrorCode.UNAUTHENTICATED -> R.string.api_error_unauthenticated
    ApiErrorCode.INVALID_ARGUMENT -> R.string.api_error_invalid_argument
    ApiErrorCode.GAME_NOT_FOUND -> R.string.api_error_game_not_found
    ApiErrorCode.GAME_NOT_JOINABLE -> R.string.api_error_game_not_joinable
    ApiErrorCode.GAME_FULL -> R.string.api_error_game_full
    ApiErrorCode.ALREADY_HAS_ROLE -> R.string.api_error_already_has_role
    ApiErrorCode.REGISTRATION_REQUIRED -> R.string.api_error_registration_required
    ApiErrorCode.NOT_ALLOWED -> R.string.api_error_not_allowed
    ApiErrorCode.NOT_A_HUNTER -> R.string.api_error_not_a_hunter
    ApiErrorCode.NOT_IN_PROGRESS -> R.string.api_error_not_in_progress
    ApiErrorCode.GAME_OVER -> R.string.api_error_game_over
    ApiErrorCode.TOO_MANY_ATTEMPTS -> R.string.api_error_too_many_attempts
    ApiErrorCode.GAME_MASTER_DISABLED -> R.string.api_error_game_master_disabled
    ApiErrorCode.NOT_WAITING -> R.string.api_error_not_waiting
    ApiErrorCode.CHICKEN_CANNOT_LEAVE -> R.string.api_error_chicken_cannot_leave
    ApiErrorCode.POWER_UP_NOT_FOUND -> R.string.api_error_power_up_not_found
    ApiErrorCode.POWER_UP_TAKEN -> R.string.api_error_power_up_taken
    ApiErrorCode.POWER_UP_WRONG_ROLE -> R.string.api_error_power_up_wrong_role
    ApiErrorCode.POWER_UP_TOO_FAR -> R.string.api_error_power_up_too_far
    ApiErrorCode.POWER_UP_ALREADY_ACTIVE -> R.string.api_error_power_up_already_active
    ApiErrorCode.POSITION_UNKNOWN -> R.string.api_error_position_unknown
    ApiErrorCode.SUBMISSION_NOT_FOUND -> R.string.api_error_submission_not_found
    ApiErrorCode.SUBMISSION_ALREADY_HANDLED -> R.string.api_error_submission_already_handled
    ApiErrorCode.CHALLENGE_NOT_FOUND -> R.string.api_error_challenge_not_found
    ApiErrorCode.ALREADY_LAUNCHED -> R.string.api_error_already_launched
    ApiErrorCode.NOT_READY_TO_LAUNCH -> R.string.api_error_not_ready_to_launch
    ApiErrorCode.NOT_A_DEBUG_GAME -> R.string.api_error_not_a_debug_game
    ApiErrorCode.NETWORK -> R.string.api_error_network
    ApiErrorCode.UNKNOWN -> R.string.api_error_unknown
}

@StringRes
fun Throwable.errorMessageRes(): Int = toApiErrorCode().messageRes()
