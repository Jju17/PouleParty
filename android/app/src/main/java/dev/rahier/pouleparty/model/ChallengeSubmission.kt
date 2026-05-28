package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp

data class ChallengeSubmission(
    val id: String = "",
    val challengeId: String = "",
    val hunterId: String = "",
    val type: String = ChallengeType.ONE_SHOT.firestoreValue,
    val submittedAt: Timestamp? = null,
    val mediaUrl: String = "",
    val mediaType: String = SubmissionMediaType.IMAGE.firestoreValue,
    val status: String = SubmissionStatus.PENDING.firestoreValue,
    val validatedBy: String? = null,
    val validatedAt: Timestamp? = null,
) {
    val typeEnum: ChallengeType get() = ChallengeType.fromFirestore(type)
    val statusEnum: SubmissionStatus get() = SubmissionStatus.fromFirestore(status)
    val mediaTypeEnum: SubmissionMediaType get() = SubmissionMediaType.fromFirestore(mediaType)
}

enum class SubmissionStatus(val firestoreValue: String) {
    PENDING("pending"),
    VALIDATED("validated"),
    REJECTED("rejected");

    companion object {
        fun fromFirestore(value: String?): SubmissionStatus =
            values().firstOrNull { it.firestoreValue == value } ?: PENDING
    }
}

enum class SubmissionMediaType(val firestoreValue: String) {
    IMAGE("image"),
    VIDEO("video");

    companion object {
        fun fromFirestore(value: String?): SubmissionMediaType =
            values().firstOrNull { it.firestoreValue == value } ?: IMAGE
    }
}
