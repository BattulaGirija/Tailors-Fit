package com.tailorsfit.pattern.blouse

import com.tailorsfit.pattern.i18n.I18n
import com.tailorsfit.pattern.i18n.tr

import com.tailorsfit.pattern.model.DraftOptions
import com.tailorsfit.pattern.model.GarmentModel
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.Pattern

enum class NeckShape {
    ROUND, U, V, SQUARE, BOAT, SWEETHEART,
    /** Paan / betel-leaf: rounded shoulders narrowing to a point at the centre. */
    LEAF,
    /** Pot / matka: wide, deep and flat at the bottom. */
    POT,
    ;

    val label: String get() = tr("neck.${name.lowercase()}")
}

/**
 * How a neckline is drawn. [widen] moves the neck point along the shoulder away from the
 * body's natural neck (cm); [depthFactor] scales the customer's neck depth measurement.
 */
data class NeckSpec(val shape: NeckShape, val widen: Double = 0.0, val depthFactor: Double = 1.0)

/** [typicalLengthInch]: usual sleeve length for pictures of the style (0 = no length to measure). */
enum class SleeveStyle(val typicalLengthInch: Double) {
    SLEEVELESS(0.0), CAP(0.0), SHORT(6.0), ELBOW(11.0), THREE_QUARTER(15.0),
    /** Gathered at the cap and the hem, finished with a band. */
    PUFF(6.0),
    /** Flares out to a wide hem. */
    BELL(9.0),
    /** Short sleeve with a gathered frill at the hem. */
    FRILL(6.0),
    ;

    /** Standard measurements with this style's usual sleeve length (for sketches). */
    fun sketchMeasurements(base: Measurements = Measurements.defaults()): Measurements =
        if (typicalLengthInch > 0) base.with(MeasurementField.SLEEVE_LENGTH, typicalLengthInch * 2.54)
            .with(MeasurementField.SLEEVE_OPENING, base[MeasurementField.SLEEVE_OPENING] - (typicalLengthInch - 6.0).coerceAtLeast(0.0) * 0.35)
        else base

    val label: String get() = tr("sleeve.${name.lowercase()}")
}

/** Extra detail on the back bodice. */
enum class BackDetail {
    NONE,
    /** A teardrop-shaped opening below the back neck. */
    KEYHOLE,
    /** Tie-up strings (dori) at the back neck. */
    DORI,
    ;

    val label: String get() = tr("back.${name.lowercase()}")
}

/** Where the blouse opens (hooks / zip). The other centre is cut on the fold. */
enum class Opening {
    FRONT, BACK;

    val label: String get() = tr("opening.${name.lowercase()}")
}

data class BlouseModel(
    override val id: String,
    /** Name as typed (used for admin-made designs; built-in designs are translated). */
    val baseName: String,
    val baseDescription: String,
    val front: NeckSpec,
    val back: NeckSpec,
    val sleeve: SleeveStyle,
    val opening: Opening,
    /** Front shaped by curved princess seams (two panels) instead of darts. */
    val princess: Boolean = false,
    val backDetail: BackDetail = BackDetail.NONE,
    /** Mandarin (band) collar around a high neckline. */
    val collar: Boolean = false,
) : GarmentModel {
    override val categoryId = "blouse"
    override val name: String get() = if (I18n.has("model.$id.name")) tr("model.$id.name") else baseName
    override val description: String get() = if (I18n.has("model.$id.desc")) tr("model.$id.desc") else baseDescription
    override val tags: List<String>
        get() = listOfNotNull(
            tr("tag.princess").takeIf { princess },
            tr("tag.front", front.shape.label),
            tr("tag.back", back.shape.label),
            sleeve.label,
            tr("tag.collar").takeIf { collar },
            backDetail.label.takeIf { backDetail != BackDetail.NONE },
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

        // ---- Trending designs ----------------------------------------------------------
        BlouseModel(
            "blouse_puff_sweetheart", "Sweetheart, Puff Sleeves",
            "Sweetheart front, U back and gathered puff sleeves finished with a band.",
            NeckSpec(NeckShape.SWEETHEART, widen = 1.0), NeckSpec(NeckShape.U), SleeveStyle.PUFF, Opening.BACK,
        ),
        BlouseModel(
            "blouse_square_puff", "Square Neck, Puff Sleeves",
            "Wide square neck front and back with puff sleeves.",
            NeckSpec(NeckShape.SQUARE, widen = 1.5), NeckSpec(NeckShape.SQUARE, widen = 1.5), SleeveStyle.PUFF, Opening.BACK,
        ),
        BlouseModel(
            "blouse_paan_back", "Paan (Leaf) Back",
            "Round front with a betel-leaf shaped back neck.",
            NeckSpec(NeckShape.ROUND, widen = 1.0), NeckSpec(NeckShape.LEAF, widen = 1.0, depthFactor = 1.4), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_keyhole_back", "Keyhole Back",
            "Round neck with a teardrop keyhole opening at the back.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.6), SleeveStyle.SHORT, Opening.FRONT,
            backDetail = BackDetail.KEYHOLE,
        ),
        BlouseModel(
            "blouse_dori_back", "Dori Tie-up Back",
            "Round front and a deep U back held with tie-up strings (dori).",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.U, widen = 1.0, depthFactor = 1.6), SleeveStyle.SHORT, Opening.FRONT,
            backDetail = BackDetail.DORI,
        ),
        BlouseModel(
            "blouse_mandarin_collar", "Mandarin Collar",
            "High neck with a band collar, 3/4 sleeves and a back zip.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.45), NeckSpec(NeckShape.ROUND, depthFactor = 0.25), SleeveStyle.THREE_QUARTER, Opening.BACK,
            collar = true,
        ),
        BlouseModel(
            "blouse_high_neck_sleeveless", "High Neck Sleeveless",
            "Sleeveless blouse with a high band collar and back opening.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.45), NeckSpec(NeckShape.ROUND, depthFactor = 0.25), SleeveStyle.SLEEVELESS, Opening.BACK,
            collar = true,
        ),
        BlouseModel(
            "blouse_boat_bell", "Boat Neck, Bell Sleeves",
            "Boat neck with flared bell sleeves.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55),
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.BELL, Opening.BACK,
        ),
        BlouseModel(
            "blouse_v_frill", "V Neck, Frill Sleeves",
            "V neck front with ruffled frills at the sleeve hem.",
            NeckSpec(NeckShape.V, depthFactor = 1.1), NeckSpec(NeckShape.ROUND), SleeveStyle.FRILL, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_pot_neck", "Pot (Matka) Neck",
            "Round front neck with a deep pot-shaped (matka) back neck: narrow at the top, round and wide below; front hooks.",
            NeckSpec(NeckShape.ROUND, widen = 0.5), NeckSpec(NeckShape.POT, widen = 0.5, depthFactor = 1.5), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_princess_puff", "Princess Cut, Puff Sleeves",
            "Corset-style princess front with a sweetheart neck and puff sleeves.",
            NeckSpec(NeckShape.SWEETHEART, widen = 1.0), NeckSpec(NeckShape.U), SleeveStyle.PUFF, Opening.BACK,
            princess = true,
        ),
        BlouseModel(
            "blouse_v_dori_sleeveless", "Sleeveless, Deep V Tie Back",
            "Sleeveless blouse with a deep V back and tie-up strings.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.V, widen = 1.0, depthFactor = 1.5), SleeveStyle.SLEEVELESS, Opening.FRONT,
            backDetail = BackDetail.DORI,
        ),
    )
}
