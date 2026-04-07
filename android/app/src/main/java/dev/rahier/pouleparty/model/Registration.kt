package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import java.util.Date

data class Registration(
    val userId: String = "",
    val teamName: String = "",
    val paid: Boolean = false,
    val joinedAt: Timestamp = Timestamp(Date())
)
