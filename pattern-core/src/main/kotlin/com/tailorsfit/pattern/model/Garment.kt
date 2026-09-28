package com.tailorsfit.pattern.model

/** A top-level category shown on the home screen (Blouses, Kurtis ...). */
data class GarmentCategory(
    val id: String,
    val name: String,
    val description: String,
    val available: Boolean,
)

/** Options that affect drafting, independent of the chosen design. */
data class DraftOptions(
    val customerName: String = "",
)

/**
 * A design the tailor can pick, e.g. "Round neck, front opening, short sleeves".
 * Implementations turn measurements into pattern pieces.
 */
interface GarmentModel {
    val id: String
    val categoryId: String
    val name: String
    val description: String
    val tags: List<String>
    val requiredMeasurements: List<MeasurementField>

    fun draft(measurements: Measurements, options: DraftOptions = DraftOptions()): Pattern
}

object Catalog {
    val categories = listOf(
        GarmentCategory("blouse", "Saree Blouses", "Round, boat, V, sweetheart and more", available = true),
        GarmentCategory("kurti", "Kurtis", "Coming soon", available = false),
        GarmentCategory("salwar", "Salwar / Pants", "Coming soon", available = false),
        GarmentCategory("lehenga", "Lehenga / Skirts", "Coming soon", available = false),
        GarmentCategory("petticoat", "Petticoats", "Coming soon", available = false),
    )

    val models: List<GarmentModel> by lazy { com.tailorsfit.pattern.blouse.BlouseCatalog.models }

    fun modelsIn(categoryId: String) = models.filter { it.categoryId == categoryId }
    fun model(id: String) = models.firstOrNull { it.id == id }
    fun category(id: String) = categories.firstOrNull { it.id == id }
}
