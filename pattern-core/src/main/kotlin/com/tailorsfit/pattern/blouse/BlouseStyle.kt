package com.tailorsfit.pattern.blouse

import com.tailorsfit.pattern.model.DraftOptions
import com.tailorsfit.pattern.model.GarmentModel
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.Pattern

enum class NeckShape(val label: String) {
    ROUND("Round"),
    U("U"),
    V("V"),
    SQUARE("Square"),
    BOAT("Boat"),
    SWEETHEART("Sweetheart"),
}

/**
 * How a neckline is drawn. [widen] moves the neck point along the shoulder away from the
 * body's natural neck (cm); [depthFactor] scales the customer's neck depth measurement.
 */
data class NeckSpec(val shape: NeckShape, val widen: Double = 0.0, val depthFactor: Double = 1.0)

enum class SleeveStyle(val label: String) {
    SLEEVELESS("Sleeveless"),
    CAP("Cap sleeve"),
    SHORT("Short sleeve"),
    ELBOW("Elbow sleeve"),
    THREE_QUARTER("3/4 sleeve"),
}

/** Where the blouse opens (hooks / zip). The other centre is cut on the fold. */
enum class Opening(val label: String) { FRONT("Front opening"), BACK("Back opening") }

data class BlouseModel(
    override val id: String,
    override val name: String,
    override val description: String,
    val front: NeckSpec,
    val back: NeckSpec,
    val sleeve: SleeveStyle,
    val opening: Opening,
    /** Front shaped by curved princess seams (two panels) instead of darts. */
    val princess: Boolean = false,
) : GarmentModel {
    override val categoryId = "blouse"
    override val tags: List<String>
        get() = listOfNotNull(
            "Princess cut".takeIf { princess },
            front.shape.label + " front",
            back.shape.label + " back",
            sleeve.label,
            opening.label,
        )

    override val requiredMeasurements: List<MeasurementField>
        get() = MeasurementField.entries.filter { f ->
            when (f) {
                MeasurementField.SLEEVE_LENGTH, MeasurementField.SLEEVE_OPENING ->
                    sleeve != SleeveStyle.SLEEVELESS && sleeve != SleeveStyle.CAP
                MeasurementField.SLEEVE_ROUND -> sleeve != SleeveStyle.SLEEVELESS
                else -> true
            }
        }

    override fun draft(measurements: Measurements, options: DraftOptions): Pattern =
        BlouseDrafter.draft(this, measurements, options)
}

object BlouseCatalog {
    val models: List<BlouseModel> = listOf(
        BlouseModel(
            "blouse_round_classic", "Classic Round Neck",
            "Traditional saree blouse with a round neck, hooks in front and short sleeves.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_round_back_open", "Round Neck, Back Opening",
            "Round neck with a smooth front; opens at the back with hooks or a zip.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND), SleeveStyle.SHORT, Opening.BACK,
        ),
        BlouseModel(
            "blouse_boat", "Boat Neck",
            "Wide, shallow neck from shoulder to shoulder, back opening.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55),
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
        ),
        BlouseModel(
            "blouse_v", "V Neck",
            "Front V neck with a round back and front hooks.",
            NeckSpec(NeckShape.V, depthFactor = 1.1), NeckSpec(NeckShape.ROUND), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_deep_back_u", "Deep U Back",
            "Modest round front with a deep U-shaped back.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.U, widen = 1.0, depthFactor = 1.5), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_sweetheart", "Sweetheart Neck",
            "Sweetheart front with a U back, back opening.",
            NeckSpec(NeckShape.SWEETHEART, widen = 1.0), NeckSpec(NeckShape.U), SleeveStyle.SHORT, Opening.BACK,
        ),
        BlouseModel(
            "blouse_square", "Square Neck",
            "Square neck in front and back.",
            NeckSpec(NeckShape.SQUARE, widen = 1.0), NeckSpec(NeckShape.SQUARE, widen = 1.0), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_sleeveless_round", "Sleeveless Round",
            "Round neck, no sleeves, deeper round back.",
            NeckSpec(NeckShape.ROUND, widen = 1.0), NeckSpec(NeckShape.U, depthFactor = 1.2), SleeveStyle.SLEEVELESS, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_sleeveless_v_back", "Sleeveless V Back",
            "Sleeveless blouse with a round front and a deep V at the back.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.V, widen = 1.0, depthFactor = 1.4), SleeveStyle.SLEEVELESS, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_boat_elbow", "Boat Neck, Elbow Sleeves",
            "Boat neck with elbow-length sleeves. Measure sleeve length to the elbow.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55),
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.ELBOW, Opening.BACK,
        ),
        BlouseModel(
            "blouse_sweetheart_cap", "Sweetheart, Cap Sleeves",
            "Sweetheart front, U back and short cap sleeves.",
            NeckSpec(NeckShape.SWEETHEART, widen = 1.0), NeckSpec(NeckShape.U, depthFactor = 1.1), SleeveStyle.CAP, Opening.BACK,
        ),
        BlouseModel(
            "blouse_round_three_quarter", "Round Neck, 3/4 Sleeves",
            "Round neck with three-quarter sleeves. Measure sleeve length to below the elbow.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND), SleeveStyle.THREE_QUARTER, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_princess_round", "Princess Cut, Round Neck",
            "Front in two panels joined by curved princess seams through the bust point — no darts. Back opening.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.U), SleeveStyle.SHORT, Opening.BACK, princess = true,
        ),
        BlouseModel(
            "blouse_princess_front_open", "Princess Cut, Front Hooks",
            "Princess-seamed front with hooks at the centre front, round neck.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND), SleeveStyle.SHORT, Opening.FRONT, princess = true,
        ),
        BlouseModel(
            "blouse_princess_sweetheart", "Princess Cut, Sweetheart",
            "Princess-seamed front with a sweetheart neck, deep U back and cap sleeves.",
            NeckSpec(NeckShape.SWEETHEART, widen = 1.0), NeckSpec(NeckShape.U, depthFactor = 1.2), SleeveStyle.CAP, Opening.BACK,
            princess = true,
        ),
        BlouseModel(
            "blouse_princess_boat_elbow", "Princess Cut, Boat Neck",
            "Princess-seamed front, boat neck and elbow sleeves. Measure sleeve length to the elbow.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55),
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.ELBOW, Opening.BACK, princess = true,
        ),
    )
}
