package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp

data class Winner(
    var hunterId: String = "",
    var hunterName: String = "",
    var timestamp: Timestamp = Timestamp.now()
)
