package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp

data class Winner(
    val hunterId: String = "",
    val hunterName: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
