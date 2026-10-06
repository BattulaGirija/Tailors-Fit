package com.tailorsfit.pattern.kurti

import com.tailorsfit.pattern.blouse.BlouseDrafter
import com.tailorsfit.pattern.blouse.NeckShape
import com.tailorsfit.pattern.blouse.NeckSpec
import com.tailorsfit.pattern.blouse.SleeveStyle
import com.tailorsfit.pattern.geom.CubicTo
import com.tailorsfit.pattern.geom.LineTo
import com.tailorsfit.pattern.geom.PathD
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.i18n.I18n
import com.tailorsfit.pattern.i18n.tr
import com.tailorsfit.pattern.model.CutInstruction
import com.tailorsfit.pattern.model.Dart
import com.tailorsfit.pattern.model.DraftOptions
import com.tailorsfit.pattern.model.Edge
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.GarmentModel
import com.tailorsfit.pattern.model.Lengths
import com.tailorsfit.pattern.model.Marking
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.MeasurementField as F
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.Notch
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.model.Piece
import com.tailorsfit.pattern.skirt.Panels
import kotlin.math.max
import kotlin.math.min

/** Kurti silhouettes, as tailors (and Pinterest boards) name them. */
enum class KurtiCut {
    /** Straight from the hip down, usually with side slits. */
    STRAIGHT,
    /** Gently widening from the hip to the hem. */
    A_LINE,
    /** Widening a lot from the waist (flared / umbrella-lite). */
    FLARED,
    /** A-line with the back hem longer than the front. */
    HIGH_LOW,
    /** Fitted bodice to the waist and a skirt of many kalis below it. */
    ANARKALI,
    ;

    val label: String get() = tr("kurti.cut.${name.lowercase()}")
}

data class KurtiModel(
    override val id: String,
    val baseName: String,
    val baseDescription: String,
    val cut: KurtiCut,
    val front: NeckSpec,
    val back: NeckSpec,
    val sleeve: SleeveStyle,
    /** Side slits from just below the hip (straight and A-line kurtis). */
    val slits: Boolean = false,
    /** Mandarin (band) collar. */
    val collar: Boolean = false,
    /** Number of kalis in an anarkali skirt (front and back together). */
    val kalis: Int = 12,
) : GarmentModel {
    override val categoryId = "kurti"
    override val name: String get() = if (I18n.has("model.$id.name")) tr("model.$id.name") else baseName
    override val description: String get() = if (I18n.has("model.$id.desc")) tr("model.$id.desc") else baseDescription
    override val group: String get() = cut.label
    override val tags: List<String>
        get() = listOfNotNull(
            cut.label,
            tr("tag.front", front.shape.label),
            tr("tag.back", back.shape.label),
            sleeve.label,
            tr("tag.slits").takeIf { slits },
            tr("tag.collar").takeIf { collar },
            tr("tag.kalis", kalis).takeIf { cut == KurtiCut.ANARKALI },
        )

    override val requiredMeasurements: List<MeasurementField>
        get() = listOfNotNull(
            F.KURTI_LENGTH, F.BUST, F.NATURAL_WAIST, F.HIP, F.SHOULDER, F.ARMHOLE,
            F.WAIST_LENGTH, F.WAIST_TO_HIP, F.APEX_LENGTH,
            F.SLEEVE_LENGTH.takeIf { sleeve != SleeveStyle.SLEEVELESS && sleeve != SleeveStyle.CAP },
            F.SLEEVE_ROUND.takeIf { sleeve != SleeveStyle.SLEEVELESS },
            F.SLEEVE_OPENING.takeIf { sleeve != SleeveStyle.SLEEVELESS && sleeve != SleeveStyle.CAP },
            F.FRONT_NECK_DEPTH, F.BACK_NECK_DEPTH,
        )

    override fun draft(measurements: Measurements, options: DraftOptions): Pattern = KurtiDrafter.draft(this, measurements, options)
}

/**
 * Kurti drafting in the traditional inch method, half pieces cut on the fold:
 * chest / 4 + 1", waist / 4 + 1", hip / 4 + ¾" (4", 4" and 3" ease in all); ¾" shoulder slope;
 * the armhole deep enough for front + back curves to be the armhole round + 1"; a side dart
 * of 1" on the front pointing at the bust point (the front is 1" longer below it, so the side
 * seams match). Sleeves are drafted like blouse sleeves.
 */
object KurtiDrafter {
    private const val INCH = 2.54
    private const val SLOPE = 0.75 * INCH
    private const val SIDE_DART = 1.0 * INCH
    private const val SLIT_BELOW_HIP = 2.0 * INCH
    private const val HIGH_LOW_DROP = 3.0 * INCH
    private const val COLLAR_HEIGHT = 1.25 * INCH

    fun draft(model: KurtiModel, m: Measurements, options: DraftOptions = DraftOptions()): Pattern {
        val errors = m.validate(model.requiredMeasurements)
        if (errors.isNotEmpty()) throw com.tailorsfit.pattern.blouse.InvalidMeasurementsException(errors)
        val warnings = ArrayList<String>()

        val bust = m[F.BUST]
        val chestX = bust / 4 + 1.0 * INCH
        val waistX = m[F.NATURAL_WAIST] / 4 + 1.0 * INCH
        val hipX = max(m[F.HIP] / 4 + 0.75 * INCH, waistX)
        val length = m[F.KURTI_LENGTH]
        val waistY = m[F.WAIST_LENGTH]
        val hipY = min(waistY + m[F.WAIST_TO_HIP], length - 2.0 * INCH)
        if (length < hipY + 4.0 * INCH) warnings += tr("warn.kurti_short")
        val shoulderX = m[F.SHOULDER] / 2
        val neckBase = (bust / 12 + 0.25 * INCH).coerceIn(2.25 * INCH, shoulderX - 1.75 * INCH)
        val apex = Pt(bust / 10, m[F.APEX_LENGTH])

        // Armhole depth: front + back curves = armhole round + 1".
        fun arms(depth: Double): Double {
            val shoulder = Pt(shoulderX, SLOPE)
            val u = Pt(chestX, depth)
            return BlouseDrafter.armholePath(u, shoulder, true).length() + BlouseDrafter.armholePath(u, shoulder, false).length()
        }
        var lo = SLOPE + 3.0 * INCH
        var hi = 12.0 * INCH
        val target = m[F.ARMHOLE] + 1.0 * INCH
        repeat(60) { val mid = (lo + hi) / 2; if (arms(mid) < target) lo = mid else hi = mid }
        val armDepth = min((lo + hi) / 2, waistY - 2.0 * INCH)

        val flare = when (model.cut) {
            KurtiCut.STRAIGHT -> 0.5 * INCH
            KurtiCut.A_LINE, KurtiCut.HIGH_LOW -> 3.0 * INCH
            KurtiCut.FLARED -> 6.0 * INCH
            KurtiCut.ANARKALI -> 0.0
        }
        val anarkali = model.cut == KurtiCut.ANARKALI
        val front = half(model, m, true, chestX, waistX, hipX, waistY, hipY, length, flare, shoulderX, neckBase, armDepth, apex, warnings)
        val back = half(model, m, false, chestX, waistX, hipX, waistY, hipY, length, flare, shoulderX, neckBase, armDepth, apex, warnings)
        val pieces = mutableListOf(front, back)

        if (model.sleeve != SleeveStyle.SLEEVELESS) {
            pieces += BlouseDrafter.draftSleeve(model.sleeve, m, front.lengthOf(EdgeKind.ARMHOLE), back.lengthOf(EdgeKind.ARMHOLE), warnings)
        }
        if (model.collar) {
            val neck = front.lengthOf(EdgeKind.NECK) + back.lengthOf(EdgeKind.NECK)
            pieces += BlouseDrafter.bandPiece("collar", tr("piece.collar"), COLLAR_HEIGHT, neck * 2 + 1.0 * INCH, 2, tr("note.collar_kurti"))
        }
        var skirtHem = 0.0
        if (anarkali) {
            // Kalis from the waist: round the waist (+4") at the top, round the hip (+4") at
            // hip level, and a hem about four times the waist.
            val k = model.kalis
            val waistRound = m[F.NATURAL_WAIST] + 4.0 * INCH
            val skirtLength = length - waistY
            val hemRound = max(waistRound * 4, m[F.HIP] * 3)
            skirtHem = hemRound
            pieces += Panels.kali(
                "kali", tr("piece.kali"), waistRound / k, (m[F.HIP] + 4.0 * INCH) / k, hipY - waistY,
                hemRound / k, skirtLength, k, listOf(tr("note.kali", k), tr("note.kali_join")),
            )
        }
        val summary = listOf(
            tr("summary.bust") to Lengths.format(chestX * 4),
            tr("summary.waist") to Lengths.format(waistX * 4),
            tr("summary.hip") to Lengths.format(hipX * 4),
            tr("summary.armhole_depth") to Lengths.format(armDepth),
            tr("summary.length") to Lengths.format(length),
        )
        val meta = mapOf(
            "category" to "kurti",
            "sleeve" to model.sleeve.name,
            "collar" to model.collar.toString(),
            "back" to "NONE",
            "cut" to model.cut.name,
            "waistY" to waistY.toString(),
            "waistX" to waistX.toString(),
            "skirtHem" to skirtHem.toString(),
            "length" to length.toString(),
            "kalis" to model.kalis.toString(),
            "slitY" to (if (model.slits) (hipY + SLIT_BELOW_HIP).toString() else ""),
        )
        val title = model.name + if (options.customerName.isNotBlank()) " — " + options.customerName else ""
        return Pattern(title, pieces, warnings, summary, meta)
    }

    private fun half(
        model: KurtiModel,
        m: Measurements,
        isFront: Boolean,
        chestX: Double,
        waistX: Double,
        hipX: Double,
        waistY: Double,
        hipY: Double,
        length: Double,
        flare: Double,
        shoulderX: Double,
        neckBase: Double,
        armDepth: Double,
        apex: Pt,
        warnings: MutableList<String>,
    ): Piece {
        val spec = if (isFront) model.front else model.back
        val anarkali = model.cut == KurtiCut.ANARKALI
        val dart = if (isFront) SIDE_DART else 0.0
        val neckX = (neckBase + spec.widen).coerceIn(1.5 * INCH, shoulderX - 1.25 * INCH)
        val slope = SLOPE
        val neck = Pt(neckX, slope * (neckX - neckBase) / (shoulderX - neckBase))
        val shoulder = Pt(shoulderX, slope)
        val underarm = Pt(chestX, armDepth)
        val depthMeasure = if (isFront) m[F.FRONT_NECK_DEPTH] else m[F.BACK_NECK_DEPTH]
        val neckDepth = (depthMeasure * spec.depthFactor).coerceIn(neck.y + 1.5 * INCH, if (isFront) apex.y - 1.0 * INCH else armDepth + 4.0 * INCH)
        val neckPath = BlouseDrafter.neckPath(spec.shape, neck, neckDepth)
        val centreTop = neckPath.end
        val armhole = BlouseDrafter.armholePath(underarm, shoulder, isFront)

        // Below the side dart the front is [dart] longer, so the front side seam (with the dart
        // closed) matches the back.
        val bottomY = if (anarkali) waistY + dart else length + dart
        val waist = Pt(waistX, waistY + dart)
        val hip = Pt(hipX, hipY + dart)
        val hemX = hipX + flare
        val sideBottom = if (anarkali) waist else Pt(hemX, bottomY)
        val sidePath: PathD = if (anarkali) {
            PathD(sideBottom, listOf(CubicTo(Pt(waist.x, waist.y - 2.0 * INCH), Pt(underarm.x, underarm.y + 3.0 * INCH), underarm)))
        } else {
            // Hem -> hip (straight or flaring), hip -> waist -> underarm in smooth curves.
            val toHip = LineTo(hip)
            val toWaist = CubicTo(Pt(hip.x, hip.y - (hip.y - waist.y) * 0.45), Pt(waist.x, waist.y + (hip.y - waist.y) * 0.35), waist)
            val toArm = CubicTo(Pt(waist.x, waist.y - (waist.y - underarm.y) * 0.4), Pt(underarm.x, underarm.y + (waist.y - underarm.y) * 0.35), underarm)
            PathD(sideBottom, listOf(toHip, toWaist, toArm))
        }
        // High-low: the back hem drops towards the centre.
        val highLow = model.cut == KurtiCut.HIGH_LOW && !isFront
        val hemCentre = Pt(0.0, bottomY + if (highLow) HIGH_LOW_DROP else 0.0)
        val hemPath = if (highLow) {
            PathD(hemCentre, listOf(CubicTo(Pt(hemX * 0.5, hemCentre.y), Pt(hemX * 0.85, bottomY + 0.5 * INCH), sideBottom)))
        } else PathD.line(hemCentre, sideBottom)
        val edges = listOf(
            Edge(EdgeKind.FOLD, PathD.line(centreTop, hemCentre)),
            Edge(if (anarkali) EdgeKind.BELT else EdgeKind.HEM, hemPath),
            Edge(EdgeKind.SIDE, sidePath),
            Edge(EdgeKind.ARMHOLE, armhole),
            Edge(EdgeKind.SHOULDER, PathD.line(shoulder, neck)),
            Edge(EdgeKind.NECK, neckPath),
        )

        val darts = ArrayList<Dart>()
        val markings = ArrayList<Marking>()
        if (isFront) {
            // Side dart 2½" below the underarm, pointing at the bust point and ending 1" short of it.
            val dir = (sidePath.points().let { it[it.size - 2] } - underarm).normalized()
            val centre = underarm + dir * (2.5 * INCH + dart / 2)
            val tip = apex + (centre - apex).normalized() * (1.0 * INCH)
            darts += Dart(centre - dir * (dart / 2), tip, centre + dir * (dart / 2))
            markings += Marking(apex + Pt(-0.8, 0.0), apex + Pt(0.8, 0.0), Marking.Kind.GUIDE)
            markings += Marking(apex + Pt(0.0, -0.8), apex + Pt(0.0, 0.8), Marking.Kind.GUIDE)
        }
        if (anarkali) {
            // Waist dart under the bust takes in what the side seam does not.
            val intake = (chestX - waistX - 1.0 * INCH).coerceIn(0.0, 1.5 * INCH)
            val x = if (isFront) apex.x else chestX * 0.45
            if (intake > 0.3 * INCH) darts += Dart(Pt(x - intake / 2, bottomY), Pt(x, if (isFront) apex.y + 1.0 * INCH else armDepth + 1.5 * INCH), Pt(x + intake / 2, bottomY))
        }
        val grainX = chestX * 0.55
        markings += Marking(Pt(grainX, armDepth + 2.0), Pt(grainX, bottomY - 3.0), Marking.Kind.GRAIN)
        markings += Marking(Pt(0.0, waistY + dart), Pt(waistX * 0.35, waistY + dart), Marking.Kind.GUIDE)

        val notches = ArrayList<Notch>()
        armhole.pointAtDistance(7.0).let { (p, t) -> notches += Notch(p, t, double = !isFront) }
        if (model.slits && !anarkali) {
            // Slit opening: notch on the side seam where the slit starts.
            val slitY = hipY + SLIT_BELOW_HIP + dart
            val pts = sidePath.points()
            val p = pts.minBy { kotlin.math.abs(it.y - slitY) }
            notches += Notch(p, Pt(0.0, 1.0))
        }
        val notes = buildList {
            add(tr("note.fold"))
            add(tr("note.neck", spec.shape.label, Lengths.format(neckDepth)))
            if (darts.isNotEmpty()) add(tr("note.darts", darts.joinToString(" + ") { Lengths.format(it.intake) }))
            if (model.slits && !anarkali) add(tr("note.slit", Lengths.format(length - hipY - SLIT_BELOW_HIP)))
            if (anarkali) add(tr("note.bodice_waist"))
            if (highLow) add(tr("note.high_low", Lengths.format(HIGH_LOW_DROP)))
        }
        return BlouseDrafter.withOutwardNotches(
            Piece(
                id = if (isFront) "front" else "back",
                name = if (isFront) tr("piece.front") else tr("piece.back"),
                cut = CutInstruction(1, onFold = true),
                edges = edges,
                darts = darts,
                markings = markings,
                points = mapOf("apex" to apex, "underarm" to underarm, "shoulder" to shoulder, "neck" to neck),
                labelAt = Pt(chestX * 0.5, armDepth + 4.0 * INCH),
                notes = notes,
            ),
            notches,
        )
    }
}

object KurtiCatalog {
    private fun k(
        id: String, name: String, desc: String, cut: KurtiCut, front: NeckSpec, back: NeckSpec, sleeve: SleeveStyle,
        slits: Boolean = false, collar: Boolean = false, kalis: Int = 12,
    ) = KurtiModel("kurti_$id", name, desc, cut, front, back, sleeve, slits, collar, kalis)

    private val round = NeckSpec(NeckShape.ROUND)
    private val backRound = NeckSpec(NeckShape.ROUND, depthFactor = 0.8)

    val models: List<KurtiModel> = listOf(
        k("straight_round", "Straight Kurti, Round Neck", "Straight kurti with side slits, round neck and ¾ sleeves — the everyday classic.",
            KurtiCut.STRAIGHT, round, backRound, SleeveStyle.THREE_QUARTER, slits = true),
        k("straight_v", "Straight Kurti, V Neck", "Straight kurti with side slits, V neck and short sleeves.",
            KurtiCut.STRAIGHT, NeckSpec(NeckShape.V, depthFactor = 1.1), backRound, SleeveStyle.SHORT, slits = true),
        k("straight_collar", "Straight Kurti, Mandarin Collar", "Straight kurti with a band collar, side slits and full sleeves.",
            KurtiCut.STRAIGHT, NeckSpec(NeckShape.ROUND, depthFactor = 0.6), NeckSpec(NeckShape.ROUND, depthFactor = 0.5), SleeveStyle.FULL, slits = true, collar = true),
        k("straight_square", "Straight Kurti, Square Neck", "Straight kurti with a square neck, side slits and ¾ sleeves.",
            KurtiCut.STRAIGHT, NeckSpec(NeckShape.SQUARE), backRound, SleeveStyle.THREE_QUARTER, slits = true),
        k("straight_boat_puff", "Straight Kurti, Boat Neck, Puff Sleeves", "Boat neck straight kurti with puff sleeves and side slits.",
            KurtiCut.STRAIGHT, NeckSpec(NeckShape.BOAT, widen = 3.0, depthFactor = 0.6), NeckSpec(NeckShape.BOAT, widen = 3.0, depthFactor = 0.6), SleeveStyle.PUFF, slits = true),
        k("aline_round", "A-Line Kurti, Round Neck", "A-line kurti widening gently from the hip, round neck, ¾ sleeves.",
            KurtiCut.A_LINE, round, backRound, SleeveStyle.THREE_QUARTER),
        k("aline_boat", "A-Line Kurti, Boat Neck, Sleeveless", "Sleeveless A-line kurti with a boat neck.",
            KurtiCut.A_LINE, NeckSpec(NeckShape.BOAT, widen = 3.0, depthFactor = 0.6), NeckSpec(NeckShape.BOAT, widen = 3.0, depthFactor = 0.7), SleeveStyle.SLEEVELESS),
        k("aline_sweetheart", "A-Line Kurti, Sweetheart Neck", "A-line kurti with a sweetheart neck and elbow sleeves.",
            KurtiCut.A_LINE, NeckSpec(NeckShape.SWEETHEART), backRound, SleeveStyle.ELBOW),
        k("aline_collar", "A-Line Kurti, Collar, Full Sleeves", "A-line kurti with a mandarin collar and full sleeves.",
            KurtiCut.A_LINE, NeckSpec(NeckShape.ROUND, depthFactor = 0.6), NeckSpec(NeckShape.ROUND, depthFactor = 0.5), SleeveStyle.FULL, collar = true),
        k("flared_u_bell", "Flared Kurti, U Neck, Bell Sleeves", "Flared kurti with a U neck and bell sleeves.",
            KurtiCut.FLARED, NeckSpec(NeckShape.U), backRound, SleeveStyle.BELL),
        k("high_low", "High-Low Kurti", "A-line kurti with a longer, curved back hem, round neck and short sleeves.",
            KurtiCut.HIGH_LOW, round, backRound, SleeveStyle.SHORT),
        k("anarkali_round", "Anarkali, 12 Kalis", "Fitted bodice and a 12-kali flared skirt, round neck, full sleeves.",
            KurtiCut.ANARKALI, round, backRound, SleeveStyle.FULL, kalis = 12),
        k("anarkali_umbrella", "Umbrella Anarkali, 16 Kalis", "Very full 16-kali anarkali with a V neck and ¾ sleeves.",
            KurtiCut.ANARKALI, NeckSpec(NeckShape.V, depthFactor = 1.1), backRound, SleeveStyle.THREE_QUARTER, kalis = 16),
        k("anarkali_square", "Sleeveless Anarkali, Square Neck", "Sleeveless anarkali with a square neck and 12 kalis.",
            KurtiCut.ANARKALI, NeckSpec(NeckShape.SQUARE), NeckSpec(NeckShape.U, depthFactor = 1.0), SleeveStyle.SLEEVELESS, kalis = 12),
    )
}
