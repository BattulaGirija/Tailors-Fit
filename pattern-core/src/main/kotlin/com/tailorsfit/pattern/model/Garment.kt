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

    /** Designs that ship with the app. */
    val builtIn: List<GarmentModel> by lazy { com.tailorsfit.pattern.blouse.BlouseCatalog.models }

    /** Designs added by an admin (kept by the app and handed in with [configure]). */
    @Volatile
    var custom: List<GarmentModel> = emptyList()
        private set

    /** Ids of designs an admin has hidden from tailors. */
    @Volatile
    var hidden: Set<String> = emptySet()
        private set

    fun configure(custom: List<GarmentModel>, hidden: Set<String>) {
        this.custom = custom
        this.hidden = hidden
    }

    /** Every design, including hidden ones (for the admin). */
    val allModels: List<GarmentModel> get() = builtIn + custom

    /** Designs tailors can pick. */
    val models: List<GarmentModel> get() = allModels.filter { it.id !in hidden }

    fun modelsIn(categoryId: String) = models.filter { it.categoryId == categoryId }

    /** Looks a design up even if hidden, so saved work keeps opening. */
    fun model(id: String) = allModels.firstOrNull { it.id == id }
    fun category(id: String) = categories.firstOrNull { it.id == id }
}
