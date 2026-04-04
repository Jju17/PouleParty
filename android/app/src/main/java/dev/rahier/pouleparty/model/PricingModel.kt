package dev.rahier.pouleparty.model

enum class PricingModel(val firestoreValue: String, val title: String) {
    FREE("free", "Free"),
    FLAT("flat", "Forfait"),
    DEPOSIT("deposit", "Caution + %");

    companion object {
        fun fromFirestore(value: String): PricingModel =
            entries.firstOrNull { it.firestoreValue == value } ?: FREE
    }
}
