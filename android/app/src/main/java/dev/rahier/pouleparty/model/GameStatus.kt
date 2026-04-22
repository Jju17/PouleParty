package dev.rahier.pouleparty.model

enum class GameStatus(val firestoreValue: String) {
    WAITING("waiting"),
    IN_PROGRESS("inProgress"),
    DONE("done"),

    // Transient states for Stripe-paid Forfait creation: the doc is created
    // via `createCreatorPaymentSheet` in `pending_payment`, then flipped to
    // `waiting` by the Stripe webhook on `payment_intent.succeeded`, or to
    // `payment_failed` on `payment_intent.payment_failed`.
    PENDING_PAYMENT("pending_payment"),
    PAYMENT_FAILED("payment_failed");

    companion object {
        fun fromFirestore(value: String): GameStatus =
            entries.firstOrNull { it.firestoreValue == value } ?: WAITING
    }
}
