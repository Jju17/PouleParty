package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.firestore.GeoPoint
import java.util.Date

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
) {
    companion object {
        /**
         * Decodes from a Realtime Database snapshot (PP-102).
         * Schema: `{ lat: Double, lng: Double, ts: <epoch ms>, invisible: Bool? }`.
         */
        fun fromRtdb(snapshot: DataSnapshot): ChickenLocation? {
            val lat = rtdbDouble(snapshot.child("lat").value) ?: return null
            val lng = rtdbDouble(snapshot.child("lng").value) ?: return null
            val ms = rtdbLong(snapshot.child("ts").value) ?: 0L
            val invisible = snapshot.child("invisible").getValue(Boolean::class.java) ?: false
            return ChickenLocation(GeoPoint(lat, lng), Timestamp(Date(ms)), invisible)
        }
    }
}
