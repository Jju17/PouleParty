package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.firestore.GeoPoint
import java.util.Date

data class HunterLocation(
    val hunterId: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val timestamp: Timestamp = Timestamp.now()
) {
    companion object {
        /**
         * Decodes from a Realtime Database child snapshot (PP-102). The
         * [hunterId] is the RTDB key (not stored in the payload).
         * Schema: `{ lat, lng, ts }`.
         */
        fun fromRtdb(hunterId: String, snapshot: DataSnapshot): HunterLocation? {
            val lat = rtdbDouble(snapshot.child("lat").value) ?: return null
            val lng = rtdbDouble(snapshot.child("lng").value) ?: return null
            val ms = rtdbLong(snapshot.child("ts").value) ?: 0L
            return HunterLocation(hunterId, GeoPoint(lat, lng), Timestamp(Date(ms)))
        }
    }
}
