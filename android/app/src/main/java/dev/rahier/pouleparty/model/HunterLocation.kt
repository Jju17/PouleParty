package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class HunterLocation(
    val hunterId: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val timestamp: Timestamp = Timestamp.now()
)
