package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.firestore.GeoPoint
import java.util.Date

data class ChickenLocation(
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val timestamp: Timestamp = Timestamp.now(),
    val invisible: Boolean = false
) {
    companion object {
        fun fromRtdb(snapshot: DataSnapshot): ChickenLocation? {
            val lat = rtdbDouble(snapshot.child("lat").value) ?: return null
            val lng = rtdbDouble(snapshot.child("lng").value) ?: return null
            val ms = rtdbLong(snapshot.child("ts").value) ?: 0L
            val invisible = snapshot.child("invisible").getValue(Boolean::class.java) ?: false
            return ChickenLocation(GeoPoint(lat, lng), Timestamp(Date(ms)), invisible)
        }
    }
}
