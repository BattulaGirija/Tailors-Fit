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
    /** Down to the wrist (kurtis). */
    FULL(22.0),
    ;

    /** Standard measurements with this style's usual sleeve length (for sketches). */
    fun sketchMeasurements(base: Measurements = Measurements.defaults()): Measurements =
        if (typicalLengthInch > 0) base.with(MeasurementField.SLEEVE_LENGTH, typicalLengthInch * 2.54)
            .with(MeasurementField.SLEEVE_OPENING, (base[MeasurementField.SLEEVE_OPENING] - (typicalLengthInch - 6.0).coerceAtLeast(0.0) * 0.35).coerceAtLeast(18.0))
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

/**
 * How the front is shaped, the way tailors name blouse types:
 * - 3 dart: side dart, dart under the bust and a small dart near the hooks;
 * - 4 dart: as 3 dart with the side shaping in two side darts;
 * - princess: curved seams from the armhole through the bust point to the bottom;
 * - katori: princess-style cup seams from the armhole to the bust and a separate belt below;
 * - sabyasachi: cup seams from the shoulder through the bust and a separate belt below.
 */
enum class BodyStyle {
    THREE_DART, FOUR_DART, PRINCESS, KATORI, SABYASACHI;

    val label: String get() = tr("body.${name.lowercase()}")

    /** Front cut in panels joined by curved seams (no darts on the front). */
    val panelled: Boolean get() = this == PRINCESS || this == KATORI || this == SABYASACHI

    /** Front has a separate belt (patti) below the bust. */
    val belted: Boolean get() = this == KATORI || this == SABYASACHI
}

/**
 * Shape of the lower edge of a separate yoke piece (usually cut in net): across the upper back
 * ([BlouseModel.backYoke]) or as an insert below the front neck ([BlouseModel.frontInsert]).
 */
enum class YokeShape {
    NONE, STRAIGHT, V, ROUND, SCALLOP, SWEETHEART;

    val label: String get() = tr("yoke.${name.lowercase()}")
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
    /** How the front is shaped; [princess] designs default to [BodyStyle.PRINCESS]. */
    val body: BodyStyle = if (princess) BodyStyle.PRINCESS else BodyStyle.THREE_DART,
    /** Halter: shoulders cut in close to the neck, always sleeveless. */
    val halter: Boolean = false,
    /** Wavy (scalloped) bottom edge on the front and back. */
    val bottomWaves: Boolean = false,
    /** Patti: a band across the bottom of the front, cut as its own piece (darted fronts only). */
    val patti: Boolean = false,
    /** Net (or contrast) yoke across the upper back, with this lower edge. */
    val backYoke: YokeShape = YokeShape.NONE,
    /** Net (or contrast) insert below the front neck, with this lower edge. */
    val frontInsert: YokeShape = YokeShape.NONE,
    /** Front bottom curving down towards the centre. */
    val bottomCurve: Boolean = false,
    /** Princess seam from the middle of the shoulder instead of the arm round. */
    val shoulderPrincess: Boolean = false,
) : GarmentModel {
    /** Sleeves actually drafted (a halter has none). */
    val effectiveSleeve: SleeveStyle get() = if (halter) SleeveStyle.SLEEVELESS else sleeve

    /** Whether the front really gets a patti (katori and sabyasachi already have a belt). */
    val hasPatti: Boolean get() = patti && !body.belted

    /** Curved bottom (not with a patti or bottom waves, which shape the bottom themselves). */
    val hasBottomCurve: Boolean get() = bottomCurve && !hasPatti && !bottomWaves && !body.belted

    override val categoryId = "blouse"
    override val group: String get() = body.label
    override val name: String get() = if (I18n.has("model.$id.name")) tr("model.$id.name") else baseName
    override val description: String get() = if (I18n.has("model.$id.desc")) tr("model.$id.desc") else baseDescription
    override val tags: List<String>
        get() = listOfNotNull(
            body.label,
            tr("tag.front", front.shape.label),
            tr("tag.back", back.shape.label),
            tr("tag.halter").takeIf { halter },
            effectiveSleeve.label,
            tr("tag.waves").takeIf { bottomWaves },
            tr("tag.patti").takeIf { hasPatti },
            tr("tag.curve").takeIf { hasBottomCurve },
            tr("tag.shoulder_cut").takeIf { shoulderPrincess && body == BodyStyle.PRINCESS },
            tr("tag.back_yoke", backYoke.label).takeIf { backYoke != YokeShape.NONE },
            tr("tag.front_insert", frontInsert.label).takeIf { frontInsert != YokeShape.NONE },
            tr("tag.collar").takeIf { collar },
            backDetail.label.takeIf { backDetail != BackDetail.NONE },
            opening.label,
        )

    override val requiredMeasurements: List<MeasurementField>
        get() = MeasurementField.blouse.filter { f ->
            when (f) {
                MeasurementField.SLEEVE_LENGTH, MeasurementField.SLEEVE_OPENING ->
                    effectiveSleeve != SleeveStyle.SLEEVELESS && effectiveSleeve != SleeveStyle.CAP
                MeasurementField.SLEEVE_ROUND -> effectiveSleeve != SleeveStyle.SLEEVELESS
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
        // Princess cut collection: the same necks with front (FO) or back (BO) hooks, with (WP)
        // or without (WOP) a patti, plus net yokes, net inserts, curved bottoms and shoulder cuts.
        BlouseModel(
            "blouse_pc_basic_fo_wp", "Princess Cut Blouse FO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, wide round neck front, U back, patti (band) across the bottom of the front, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_basic_fo_wop", "Princess Cut Blouse FO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, wide round neck front, U back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_basic_bo_wp", "Princess Cut Blouse BO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, wide round neck front, U back, patti (band) across the bottom of the front, short sleeves. Back hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_basic_bo_wop", "Princess Cut Blouse BO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, wide round neck front, U back, short sleeves. Back hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_boat_fo_wp", "Princess Cut Boat Neck FO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, patti (band) across the bottom of the front, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_boat_fo_wop", "Princess Cut Boat Neck FO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_boat_bo_wp", "Princess Cut Boat Neck BO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, patti (band) across the bottom of the front, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_boat_bo_wop", "Princess Cut Boat Neck BO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_close_fo_wp", "Princess Cut Close Neck FO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, close (high, narrow) round neck, patti (band) across the bottom of the front, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_close_fo_wop", "Princess Cut Close Neck FO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, close (high, narrow) round neck, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_close_bo_wp", "Princess Cut Close Neck BO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, close (high, narrow) round neck, patti (band) across the bottom of the front, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_close_bo_wop", "Princess Cut Close Neck BO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, close (high, narrow) round neck, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_halter_fo_wp", "Princess Cut Halter Neck FO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, halter: shoulders cut in close to the neck, sleeveless, patti (band) across the bottom of the front. Front hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.3), SleeveStyle.SLEEVELESS, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, patti = true, halter = true,
        ),
        BlouseModel(
            "blouse_pc_halter_fo_wop", "Princess Cut Halter Neck FO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, halter: shoulders cut in close to the neck, sleeveless. Front hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.3), SleeveStyle.SLEEVELESS, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, halter = true,
        ),
        BlouseModel(
            "blouse_pc_halter_bo_wp", "Princess Cut Halter Neck BO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, halter: shoulders cut in close to the neck, sleeveless, patti (band) across the bottom of the front. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.3), SleeveStyle.SLEEVELESS, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, patti = true, halter = true,
        ),
        BlouseModel(
            "blouse_pc_halter_bo_wop", "Princess Cut Halter Neck BO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, halter: shoulders cut in close to the neck, sleeveless. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.3), SleeveStyle.SLEEVELESS, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, halter = true,
        ),
        BlouseModel(
            "blouse_pc_high_fo_wp", "Princess Cut High Neck FO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, round front neck, high (closed) back neck, patti (band) across the bottom of the front, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_high_fo_wop", "Princess Cut High Neck FO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, round front neck, high (closed) back neck, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_high_bo_wp", "Princess Cut High Neck BO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, round front neck, high (closed) back neck, patti (band) across the bottom of the front, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_high_bo_wop", "Princess Cut High Neck BO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, round front neck, high (closed) back neck, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_close_double_bo", "Princess Cut Close Neck Double Side Shape BO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, close (high, narrow) round neck, front bottom curving down to the centre, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, bottomCurve = true,
        ),
        BlouseModel(
            "blouse_pc_close_shoulder_bo", "Princess Cut Close Neck Shoulder Middle Cut BO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, close (high, narrow) round neck, princess seam from the middle of the shoulder, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, shoulderPrincess = true,
        ),
        BlouseModel(
            "blouse_pc_bottom_curve_fo", "Princess Cut Bottom Curve FO",
            "Princess cut: two front panels joined by curved seams through the bust point, wide round neck front, U back, front bottom curving down to the centre, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, bottomCurve = true,
        ),
        BlouseModel(
            "blouse_pc_bottom_curve_bo", "Princess Cut Bottom Curve BO",
            "Princess cut: two front panels joined by curved seams through the bust point, wide round neck front, U back, front bottom curving down to the centre, short sleeves. Back hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, bottomCurve = true,
        ),
        BlouseModel(
            "blouse_pc_boat_net1", "Boat Neck Princess Cut Net Model 1",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a scalloped edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.SCALLOP,
        ),
        BlouseModel(
            "blouse_pc_boat_net2", "Boat Neck Princess Cut Net Model 2",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a V edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.V,
        ),
        BlouseModel(
            "blouse_pc_boat_net3", "Boat Neck Princess Cut Net Model 3",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a round edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.ROUND,
        ),
        BlouseModel(
            "blouse_pc_boat_net4", "Boat Neck Princess Cut Net Model 4",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a straight edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.STRAIGHT,
        ),
        BlouseModel(
            "blouse_pc_boat_net5", "Boat Neck Princess Cut Net Model 5",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a sweetheart edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.SWEETHEART,
        ),
        BlouseModel(
            "blouse_pc_boat_net6", "Boat Neck Princess Cut Net Model 6",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a V edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.V,
        ),
        BlouseModel(
            "blouse_pc_boat_net7", "Boat Neck Princess Cut Net Model 7",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a scalloped edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.SCALLOP,
        ),
        BlouseModel(
            "blouse_pc_boat_net8", "Boat Neck Princess Cut Net Model 8",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a round edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.ROUND,
        ),
        BlouseModel(
            "blouse_pc_boat_net9", "Boat Neck Princess Cut Net Model 9",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a straight edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.STRAIGHT,
        ),
        BlouseModel(
            "blouse_pc_boat_net10", "Boat Neck Princess Cut Net Model 10",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a scalloped edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.SCALLOP,
        ),
        BlouseModel(
            "blouse_pc_boat_net11", "Boat Neck Princess Cut Net Model 11",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net back yoke with a round edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, backYoke = YokeShape.ROUND,
        ),
        BlouseModel(
            "blouse_pc_boat_model1", "Princess Boat Model 1",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_bengaluru_fo_wp", "Bengaluru Princess Cut Blouse FO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, wide, deep U neck with narrow straps, patti (band) across the bottom of the front, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 1.5, depthFactor = 1.3), NeckSpec(NeckShape.U, widen = 1.5, depthFactor = 1.4), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_bengaluru2_fo_wp", "Bengaluru Princess Cut FO WP",
            "Princess cut: two front panels joined by curved seams through the bust point, wide, deep U neck with narrow straps, patti (band) across the bottom of the front, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 1.5, depthFactor = 1.3), NeckSpec(NeckShape.U, widen = 1.5, depthFactor = 1.4), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS, patti = true,
        ),
        BlouseModel(
            "blouse_pc_bengaluru_boat_fo", "Bengaluru Boat Neck FO",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_bengaluru_high_bo_wop", "Bengaluru Princess Cut High Neck BO WOP",
            "Princess cut: two front panels joined by curved seams through the bust point, round front neck, high (closed) back neck, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_high_model1", "High Neck Model 1",
            "Princess cut: two front panels joined by curved seams through the bust point, high neck front and back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.45), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            princess = true, body = BodyStyle.PRINCESS,
        ),
        BlouseModel(
            "blouse_pc_high_insert1", "Princess High Neck Model 1",
            "Princess cut: two front panels joined by curved seams through the bust point, high neck front and back, net insert below the front neck with a sweetheart edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.45), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.SWEETHEART,
        ),
        BlouseModel(
            "blouse_pc_high_insert2", "Princess High Neck Model 2",
            "Princess cut: two front panels joined by curved seams through the bust point, high neck front and back, net insert below the front neck with a V edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.45), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.V,
        ),
        BlouseModel(
            "blouse_pc_high_insert3", "Princess High Neck Model 3",
            "Princess cut: two front panels joined by curved seams through the bust point, high neck front and back, net insert below the front neck with a scalloped edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.45), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.SCALLOP,
        ),
        BlouseModel(
            "blouse_pc_high_insert4", "Princess High Neck Model 4",
            "Princess cut: two front panels joined by curved seams through the bust point, high neck front and back, net insert below the front neck with a round edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.45), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.ROUND,
        ),
        BlouseModel(
            "blouse_pc_boat_insert1", "Boat Neck With WOP Model 1",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net insert below the front neck with a round edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.ROUND,
        ),
        BlouseModel(
            "blouse_pc_boat_insert2", "Boat Neck With WOP Model 2",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net insert below the front neck with a scalloped edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.SCALLOP,
        ),
        BlouseModel(
            "blouse_pc_boat_insert3", "Boat Neck With WOP Model 3",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net insert below the front neck with a V edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.V,
        ),
        BlouseModel(
            "blouse_pc_boat_insert4", "Boat Neck With WOP Model 4",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net insert below the front neck with a sweetheart edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.SWEETHEART,
        ),
        BlouseModel(
            "blouse_pc_boat_insert5", "Boat Neck With WOP Model 5",
            "Princess cut: two front panels joined by curved seams through the bust point, boat neck front and back, net insert below the front neck with a straight edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, frontInsert = YokeShape.STRAIGHT,
        ),
        BlouseModel(
            "blouse_pc_halter_insert1", "Halter Neck With WOP New Model 1",
            "Princess cut: two front panels joined by curved seams through the bust point, halter: shoulders cut in close to the neck, sleeveless, net insert below the front neck with a sweetheart edge. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.ROUND, depthFactor = 0.5), SleeveStyle.SLEEVELESS, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, halter = true, frontInsert = YokeShape.SWEETHEART,
        ),
        BlouseModel(
            "blouse_pc_halter_insert2", "Halter Neck With WOP New Model 2",
            "Princess cut: two front panels joined by curved seams through the bust point, halter: shoulders cut in close to the neck, sleeveless, net insert below the front neck with a V edge. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.ROUND, depthFactor = 0.5), SleeveStyle.SLEEVELESS, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, halter = true, frontInsert = YokeShape.V,
        ),
        BlouseModel(
            "blouse_pc_halter_insert3", "Halter Neck With WOP New Model 3",
            "Princess cut: two front panels joined by curved seams through the bust point, halter: shoulders cut in close to the neck, sleeveless, net insert below the front neck with a round edge. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.ROUND, depthFactor = 0.5), SleeveStyle.SLEEVELESS, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, halter = true, frontInsert = YokeShape.ROUND,
        ),
        BlouseModel(
            "blouse_pc_halter_insert4", "Halter Neck With WOP New Model 4",
            "Princess cut: two front panels joined by curved seams through the bust point, halter: shoulders cut in close to the neck, sleeveless, net insert below the front neck with a scalloped edge. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.ROUND, depthFactor = 0.5), SleeveStyle.SLEEVELESS, Opening.BACK,
            princess = true, body = BodyStyle.PRINCESS, halter = true, frontInsert = YokeShape.SCALLOP,
        ),
        // 3 dart collection (the common tailor's list): basic, boat, close and high necks,
        // halter and bottom waves, each with front (FO) or back (BO) opening.
        BlouseModel(
            "blouse_3d_basic_fo", "3 Dart Basic Blouse FO",
            "Basic 3 dart blouse: wide round neck front, U back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_3d_basic_bo", "3 Dart Basic Blouse BO",
            "Basic 3 dart blouse: wide round neck front, U back, short sleeves. Back hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
        ),
        BlouseModel(
            "blouse_3d_boat_fo", "3 Dart Boat Neck FO",
            "Boat neck front and back, 3 darts, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_3d_boat_bo", "3 Dart Boat Neck BO",
            "Boat neck front and back, 3 darts, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
        ),
        BlouseModel(
            "blouse_3d_close_fo", "3 Dart Close Neck FO",
            "Close (high, narrow) round neck front and back, 3 darts, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_3d_close_bo", "3 Dart Close Neck BO",
            "Close (high, narrow) round neck front and back, 3 darts, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
        ),
        BlouseModel(
            "blouse_3d_halter_fo", "3 Dart Halter Neck FO",
            "Halter: shoulders cut in close to the neck, round front, deep U back, sleeveless. Front hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.5), SleeveStyle.SLEEVELESS, Opening.FRONT,
            halter = true,
        ),
        BlouseModel(
            "blouse_3d_halter_bo", "3 Dart Halter Neck BO",
            "Halter: shoulders cut in close to the neck, round front, U back, sleeveless. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.2), SleeveStyle.SLEEVELESS, Opening.BACK,
            halter = true,
        ),
        BlouseModel(
            "blouse_3d_high_fo", "3 Dart High Neck FO",
            "Round front neck with a high (closed) back neck, 3 darts, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_3d_high_bo", "3 Dart High Neck BO",
            "Round front neck with a high (closed) back neck, 3 darts, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
        ),
        BlouseModel(
            "blouse_3d_basic_fo_bw", "3 Dart Basic Blouse FO BW",
            "Basic 3 dart blouse with a wavy (scalloped) bottom edge, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.FRONT,
            bottomWaves = true,
        ),
        BlouseModel(
            "blouse_3d_basic_bo_bw", "3 Dart Basic Blouse Back Open Bottom Waves",
            "Basic 3 dart blouse with a wavy (scalloped) bottom edge, short sleeves. Back hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
            bottomWaves = true,
        ),
        BlouseModel(
            "blouse_bengaluru_fo", "Bengaluru Blouse Model FO",
            "Bengaluru model: wide, deep U front neck with narrow straps, deep U back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 1.5, depthFactor = 1.3), NeckSpec(NeckShape.U, widen = 1.5, depthFactor = 1.4), SleeveStyle.SHORT, Opening.FRONT,
        ),
        BlouseModel(
            "blouse_3d_model1", "Basic Blouse 3 Dart Model 1",
            "Sweetheart front neck, round back, 3 darts, short sleeves. Back hooks.",
            NeckSpec(NeckShape.SWEETHEART, widen = 0.5), NeckSpec(NeckShape.ROUND), SleeveStyle.SHORT, Opening.BACK,
        ),
        // 4 dart collection: the same necks, front (FO) or back (BO) hooks, with (WP) or
        // without (WOP) a patti across the bottom of the front.
        BlouseModel(
            "blouse_4d_basic_fo_wp", "4 Dart Basic Blouse FO WP",
            "Basic 4 dart blouse: wide round neck front, U back, short sleeves. With patti (band) across the bottom of the front. Front hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART, patti = true,
        ),
        BlouseModel(
            "blouse_4d_basic_fo_wop", "4 Dart Basic Blouse FO WOP",
            "Basic 4 dart blouse: wide round neck front, U back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART, patti = false,
        ),
        BlouseModel(
            "blouse_4d_basic_bo_wp", "4 Dart Basic Blouse BO WP",
            "Basic 4 dart blouse: wide round neck front, U back, short sleeves. With patti (band) across the bottom of the front. Back hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.FOUR_DART, patti = true,
        ),
        BlouseModel(
            "blouse_4d_basic_bo_wop", "4 Dart Basic Blouse BO WOP",
            "Basic 4 dart blouse: wide round neck front, U back, short sleeves. Back hooks.",
            NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.1), NeckSpec(NeckShape.U, widen = 0.5, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.FOUR_DART, patti = false,
        ),
        BlouseModel(
            "blouse_4d_boat_fo_wp", "4 Dart Boat Neck FO WP",
            "Boat neck front and back, 4 darts, short sleeves. With patti (band) across the bottom of the front. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART, patti = true,
        ),
        BlouseModel(
            "blouse_4d_boat_fo_wop", "4 Dart Boat Neck FO WOP",
            "Boat neck front and back, 4 darts, short sleeves. Front hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART, patti = false,
        ),
        BlouseModel(
            "blouse_4d_boat_bo_wp", "4 Dart Boat Neck BO WP",
            "Boat neck front and back, 4 darts, short sleeves. With patti (band) across the bottom of the front. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.FOUR_DART, patti = true,
        ),
        BlouseModel(
            "blouse_4d_boat_bo_wop", "4 Dart Boat Neck BO WOP",
            "Boat neck front and back, 4 darts, short sleeves. Back hooks.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55), NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.FOUR_DART, patti = false,
        ),
        BlouseModel(
            "blouse_4d_close_fo_wp", "4 Dart Close Neck FO WP",
            "Close (high, narrow) round neck front and back, 4 darts, short sleeves. With patti (band) across the bottom of the front. Front hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART, patti = true,
        ),
        BlouseModel(
            "blouse_4d_close_fo_wop", "4 Dart Close Neck FO WOP",
            "Close (high, narrow) round neck front and back, 4 darts, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART, patti = false,
        ),
        BlouseModel(
            "blouse_4d_close_bo_wp", "4 Dart Close Neck BO WP",
            "Close (high, narrow) round neck front and back, 4 darts, short sleeves. With patti (band) across the bottom of the front. Back hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.FOUR_DART, patti = true,
        ),
        BlouseModel(
            "blouse_4d_close_bo_wop", "4 Dart Close Neck BO WOP",
            "Close (high, narrow) round neck front and back, 4 darts, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.5), NeckSpec(NeckShape.ROUND, widen = -2.5, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.FOUR_DART, patti = false,
        ),
        BlouseModel(
            "blouse_4d_halter_fo_wp", "4 Dart Halter Neck FO WP",
            "Halter: shoulders cut in close to the neck, round front, U back, 4 darts, sleeveless. With patti (band) across the bottom of the front. Front hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.3), SleeveStyle.SLEEVELESS, Opening.FRONT,
            body = BodyStyle.FOUR_DART, halter = true, patti = true,
        ),
        BlouseModel(
            "blouse_4d_halter_fo_wop", "4 Dart Halter Neck FO WOP",
            "Halter: shoulders cut in close to the neck, round front, U back, 4 darts, sleeveless. Front hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.3), SleeveStyle.SLEEVELESS, Opening.FRONT,
            body = BodyStyle.FOUR_DART, halter = true, patti = false,
        ),
        BlouseModel(
            "blouse_4d_halter_bo_wp", "4 Dart Halter Neck BO WP",
            "Halter: shoulders cut in close to the neck, round front, U back, 4 darts, sleeveless. With patti (band) across the bottom of the front. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.3), SleeveStyle.SLEEVELESS, Opening.BACK,
            body = BodyStyle.FOUR_DART, halter = true, patti = true,
        ),
        BlouseModel(
            "blouse_4d_halter_bo_wop", "4 Dart Halter Neck BO WOP",
            "Halter: shoulders cut in close to the neck, round front, U back, 4 darts, sleeveless. Back hooks.",
            NeckSpec(NeckShape.ROUND, depthFactor = 0.8), NeckSpec(NeckShape.U, depthFactor = 1.3), SleeveStyle.SLEEVELESS, Opening.BACK,
            body = BodyStyle.FOUR_DART, halter = true, patti = false,
        ),
        BlouseModel(
            "blouse_4d_high_fo_wp", "4 Dart High Neck FO WP",
            "Round front neck with a high (closed) back neck, 4 darts, short sleeves. With patti (band) across the bottom of the front. Front hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART, patti = true,
        ),
        BlouseModel(
            "blouse_4d_high_fo_wop", "4 Dart High Neck FO WOP",
            "Round front neck with a high (closed) back neck, 4 darts, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART, patti = false,
        ),
        BlouseModel(
            "blouse_4d_high_bo_wp", "4 Dart High Neck BO WP",
            "Round front neck with a high (closed) back neck, 4 darts, short sleeves. With patti (band) across the bottom of the front. Back hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.FOUR_DART, patti = true,
        ),
        BlouseModel(
            "blouse_4d_high_bo_wop", "4 Dart High Neck BO WOP",
            "Round front neck with a high (closed) back neck, 4 darts, short sleeves. Back hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND, depthFactor = 0.35), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.FOUR_DART, patti = false,
        ),
        BlouseModel(
            "blouse_bengaluru_4d_fo", "Bengaluru 4 Dart Model FO",
            "Bengaluru model with 4 darts: wide, deep U front neck with narrow straps, deep U back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 1.5, depthFactor = 1.3), NeckSpec(NeckShape.U, widen = 1.5, depthFactor = 1.4), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART,
        ),
        BlouseModel(
            "blouse_4dart_round", "4 Dart Round Neck",
            "Round neck with two side darts for a smooth fit over a fuller bust. Front hooks.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.ROUND), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.FOUR_DART,
        ),
        BlouseModel(
            "blouse_4dart_boat_elbow", "4 Dart Boat Neck, Elbow Sleeves",
            "Boat neck front and back, two side darts, elbow sleeves. Back opening.",
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.55),
            NeckSpec(NeckShape.BOAT, widen = 4.0, depthFactor = 0.8), SleeveStyle.ELBOW, Opening.BACK,
            body = BodyStyle.FOUR_DART,
        ),
        BlouseModel(
            "blouse_katori_sweetheart", "Katori Sweetheart",
            "Katori cups from the armhole to the bust with a belt below, sweetheart neck, U back.",
            NeckSpec(NeckShape.SWEETHEART, widen = 1.0), NeckSpec(NeckShape.U, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.KATORI,
        ),
        BlouseModel(
            "blouse_katori_round", "Katori Round Neck, Front Hooks",
            "Katori cups and belt, round neck, deep U back. Opens at the front.",
            NeckSpec(NeckShape.ROUND), NeckSpec(NeckShape.U, widen = 1.0, depthFactor = 1.4), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.KATORI,
        ),
        BlouseModel(
            "blouse_sabyasachi_square", "Sabyasachi Square Neck",
            "Cup seams from the shoulder through the bust, a belt below, square neck, deep V back, elbow sleeves.",
            NeckSpec(NeckShape.SQUARE, widen = 1.0), NeckSpec(NeckShape.V, widen = 1.0, depthFactor = 1.4), SleeveStyle.ELBOW, Opening.BACK,
            body = BodyStyle.SABYASACHI,
        ),
        BlouseModel(
            "blouse_sabyasachi_v", "Sabyasachi Deep V",
            "Deep V front with shoulder-to-bust cup seams and a belt, U back, short sleeves.",
            NeckSpec(NeckShape.V, depthFactor = 1.2), NeckSpec(NeckShape.U, depthFactor = 1.2), SleeveStyle.SHORT, Opening.BACK,
            body = BodyStyle.SABYASACHI,
        ),
        BlouseModel(
            "blouse_saby_new3", "Sabyasachi Model New 3",
            "Deep sweetheart front with shoulder-to-bust cup seams and a belt, round back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.SWEETHEART, widen = 0.5, depthFactor = 1.2), NeckSpec(NeckShape.ROUND, depthFactor = 0.8), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.SABYASACHI,
        ),
        BlouseModel(
            "blouse_saby_new4", "Sabyasachi Model New 4",
            "Round front neck with shoulder-to-bust cup seams and a belt, round back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.ROUND, widen = 0.5), NeckSpec(NeckShape.ROUND, depthFactor = 0.8), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.SABYASACHI,
        ),
        BlouseModel(
            "blouse_saby_new5", "Sabyasachi Model New 5",
            "Wide, deep U front with shoulder-to-bust cup seams and a belt, shallow round back, short sleeves. Front hooks.",
            NeckSpec(NeckShape.U, widen = 1.0, depthFactor = 1.2), NeckSpec(NeckShape.ROUND, widen = 1.0, depthFactor = 0.6), SleeveStyle.SHORT, Opening.FRONT,
            body = BodyStyle.SABYASACHI,
        ),
    )
}
