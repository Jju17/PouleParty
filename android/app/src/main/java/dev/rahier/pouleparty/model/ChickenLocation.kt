package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class ChickenLocation(
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val timestamp: Timestamp = Timestamp.now(),
    /**
     * `true` while the chicken has Invisibility active (PP-87). The
     * chicken keeps writing positions during Invisibility — hunters
     * filter the marker out client-side based on this flag, and the
     * GameMaster (PP-24) ignores the flag entirely. Optional with a
     * default of `false` so pre-PP-87 docs decode safely.
     */
    val invisible: Boolean = false
)
