package dev.rahier.pouleparty.model

import androidx.annotation.StringRes
import dev.rahier.pouleparty.R

enum class GameMod(val firestoreValue: String, @param:StringRes val titleRes: Int) {
    FOLLOW_THE_CHICKEN("followTheChicken", R.string.mode_follow_title),
    STAY_IN_THE_ZONE("stayInTheZone", R.string.mode_stay_title);

    companion object {
        fun fromFirestore(value: String): GameMod =
            entries.firstOrNull { it.firestoreValue == value } ?: FOLLOW_THE_CHICKEN
    }
}
