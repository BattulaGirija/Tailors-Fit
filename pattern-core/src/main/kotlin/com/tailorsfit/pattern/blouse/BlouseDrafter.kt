package com.tailorsfit.pattern.blouse

import com.tailorsfit.pattern.geom.CubicTo
import com.tailorsfit.pattern.geom.LineTo
import com.tailorsfit.pattern.geom.PathD
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Seg
import com.tailorsfit.pattern.geom.pointInPolygon
import com.tailorsfit.pattern.i18n.tr
import com.tailorsfit.pattern.model.CutInstruction
import com.tailorsfit.pattern.model.Dart
import com.tailorsfit.pattern.model.DraftOptions
import com.tailorsfit.pattern.model.Edge
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.Marking
import com.tailorsfit.pattern.model.MeasurementField as F
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.Notch
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.model.Piece
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class InvalidMeasurementsException(val errors: Map<F, String>) :
    IllegalArgumentException(errors.values.joinToString("; "))

/**
 * Drafts a fitted saree blouse (front, back and sleeve) from body measurements.
 *
 * Coordinate system for the bodice halves: x = 0 is the centre front / centre back line and x
 * grows towards the side seam; y = 0 is the level of the shoulder next to the neck (HPS) and y
 * grows downwards. All values are centimetres on the sewing line; seam allowances are added
 * later from [com.tailorsfit.pattern.model.SeamAllowances].
 */
object BlouseDrafter {
    /** Shoulder drop from the neck point to the shoulder tip. */
    const val SHOULDER_SLOPE = 3.0
    /** Total ease at bust and waist (split between front and back). */
    const val BUST_EASE = 4.0
    const val WAIST_EASE = 3.0
    const val SLEEVE_EASE = 2.5
    /** Extra length of the sleeve cap over the armhole, eased in when sewing. */
    const val CAP_EASE = 1.0
    /** Distance of the balance notches from the underarm, measured along the seam. */
    const val ARMHOLE_NOTCH_FROM_UNDERARM = 7.0
    /** Puff sleeves: cap widened by this factor and raised, gathered into the armhole. */
    const val PUFF_WIDTH = 1.35
    const val PUFF_EXTRA_CAP = 3.0
    /** Bell sleeves: hem width relative to the biceps. */
    const val BELL_FLARE = 1.6
    const val COLLAR_HEIGHT = 3.5
    /** Where the princess seam meets the armhole, as a fraction of armhole length from the underarm. */
    const val PRINCESS_ARMHOLE_FRACTION = 0.5

    fun draft(model: BlouseModel, m: Measurements, options: DraftOptions = DraftOptions()): Pattern {
        val errors = m.validate(model.requiredMeasurements)
        if (errors.isNotEmpty()) throw InvalidMeasurementsException(errors)

        val warnings = ArrayList<String>()
        val bodice = BodiceFrame.create(model, m, warnings)

        val fronts = draftBodiceHalf(bodice, isFront = true, model, m, warnings)
        val back = draftBodiceHalf(bodice, isFront = false, model, m, warnings).single()
        val pieces = (fronts + back).toMutableList()

        val frontArm = fronts.sumOf { it.lengthOf(EdgeKind.ARMHOLE) }
        val backArm = back.lengthOf(EdgeKind.ARMHOLE)
        val summary = mutableListOf(
            tr("summary.bust") to cm(bodice.frontBust * 2 + bodice.backBust * 2),
            tr("summary.waist") to cm((bodice.frontWaist + bodice.backWaist) * 2),
            tr("summary.armhole_drafted") to cm(frontArm + backArm),
            tr("summary.armhole_measured") to cm(m[F.ARMHOLE]),
        )

        if (model.sleeve != SleeveStyle.SLEEVELESS) {
            val sleevePieces = draftSleeve(model, m, frontArm, backArm, warnings)
            val sleeve = sleevePieces.first()
            pieces += sleevePieces
            summary += tr("summary.cap_height") to cm(sleeve.points.getValue("capHeight").y)
            summary += tr("summary.cap_length") to cm(sleeve.lengthOf(EdgeKind.SLEEVE_CAP))
        } else {
            warnings += tr("warn.sleeveless")
        }

        if (model.collar) pieces += collarPiece(model, fronts, back)
        if (model.backDetail == BackDetail.DORI) pieces += bandPiece("tie", tr("piece.tie"), 3.0, 45.0, 2, tr("note.tie"))
        if (model.backDetail == BackDetail.KEYHOLE) {
            val i = pieces.indexOfFirst { it.id == "back" }
            pieces[i] = withKeyhole(pieces[i], bodice, warnings)
        }

        val title = buildString {
            append(model.name)
            if (options.customerName.isNotBlank()) append(" — ").append(options.customerName)
        }
        val meta = mapOf(
            "sleeve" to model.sleeve.name,
            "collar" to model.collar.toString(),
            "back" to model.backDetail.name,
        )
        return Pattern(title, pieces, warnings, summary, meta)
    }

    /** Values shared by front and back so that shoulders and side seams match. */
    internal data class BodiceFrame(
        val shoulderX: Double,
        val neckBase: Double,
        val neckX: Double,
        val armDepth: Double,
        val frontBust: Double,
        val backBust: Double,
        val frontWaist: Double,
        val backWaist: Double,
        val apexX: Double,
        val apexY: Double,
        val frontLength: Double,
        val backLength: Double,
    ) {
        fun shoulderY(x: Double) = SHOULDER_SLOPE * (x - neckBase) / (shoulderX - neckBase)
        val neckPoint get() = Pt(neckX, shoulderY(neckX))
        val shoulderTip get() = Pt(shoulderX, SHOULDER_SLOPE)

        companion object {
            fun create(model: BlouseModel, m: Measurements, warnings: MutableList<String>): BodiceFrame {
                var shoulderX = m[F.SHOULDER] / 2
                if (model.sleeve == SleeveStyle.SLEEVELESS) shoulderX -= 1.0 // keeps straps off the arm
                val neckBase = (m[F.SHOULDER] * 0.2).coerceIn(6.0, 8.5)
                val widen = max(model.front.widen, model.back.widen)
                val neckX = min(neckBase + widen, shoulderX - 3.0)
                if (neckBase + widen > shoulderX - 3.0) warnings += tr("warn.neck_wide")

                val frontLength = m[F.FRONT_LENGTH]
                val backLength = m[F.BACK_LENGTH]
                var armDepth = m[F.ARMHOLE] / 2
                val maxArm = min(frontLength, backLength) - 6.0
                if (armDepth > maxArm) {
                    warnings += tr("warn.armhole_deep", cm(maxArm))
                    armDepth = maxArm
                }
                if (armDepth < SHOULDER_SLOPE + 8) armDepth = SHOULDER_SLOPE + 8

                val apexX = m[F.APEX_TO_APEX] / 2
                val dx = apexX - neckBase
                val apexLen = m[F.APEX_LENGTH]
                var apexY = if (apexLen > kotlin.math.abs(dx)) sqrt(apexLen * apexLen - dx * dx) else apexLen
                if (apexY > frontLength - 4) {
                    warnings += tr("warn.apex_low")
                    apexY = frontLength - 4
                }
                if (frontLength < backLength) {
                    warnings += tr("warn.front_short")
                }
                if (m[F.WAIST] > m[F.BUST]) warnings += tr("warn.waist_big")

                return BodiceFrame(
                    shoulderX = shoulderX,
                    neckBase = neckBase,
                    neckX = neckX,
                    armDepth = armDepth,
                    frontBust = m[F.BUST] / 4 + BUST_EASE / 4 + 0.5,
                    backBust = m[F.BUST] / 4 + BUST_EASE / 4 - 0.5,
                    frontWaist = m[F.WAIST] / 4 + WAIST_EASE / 4 + 0.25,
                    backWaist = m[F.WAIST] / 4 + WAIST_EASE / 4 - 0.25,
                    apexX = apexX,
                    apexY = apexY,
                    frontLength = frontLength,
                    backLength = backLength,
                )
            }
        }
    }

    private fun draftBodiceHalf(
        f: BodiceFrame,
        isFront: Boolean,
        model: BlouseModel,
        m: Measurements,
        warnings: MutableList<String>,
    ): List<Piece> {
        val spec = if (isFront) model.front else model.back
        val bust = if (isFront) f.frontBust else f.backBust
        val waist = if (isFront) f.frontWaist else f.backWaist
        val centreLength = if (isFront) f.frontLength else f.backLength
        val sideBottomY = max(f.frontLength, f.backLength).let { if (isFront) it else f.backLength }

        // Width lost between bust and waist: some at the side seam, the rest in a waist dart.
        val excess = bust - waist
        var sideInset: Double
        var dartIntake: Double
        if (excess <= 0) {
            sideInset = excess // flares out
            dartIntake = 0.0
        } else {
            sideInset = min(excess * 0.35, 2.5)
            dartIntake = excess - sideInset
            val maxDart = min(7.0, (f.apexX - 1.0) * 2)
            if (dartIntake > maxDart) {
                sideInset += dartIntake - maxDart
                dartIntake = maxDart
            }
        }

        val neck = f.neckPoint
        val shoulder = f.shoulderTip
        val underarm = Pt(bust, f.armDepth)
        val sideBottom = Pt(bust - sideInset, sideBottomY)
        val hemCentre = Pt(0.0, centreLength)

        val depthMeasure = if (isFront) m[F.FRONT_NECK_DEPTH] else m[F.BACK_NECK_DEPTH]
        var neckDepth = depthMeasure * spec.depthFactor
        val minDepth = neck.y + 1.5
        val maxDepth = if (isFront) f.apexY - 2.0 else centreLength - 6.0
        val who = if (isFront) tr("piece.front") else tr("piece.back")
        if (neckDepth < minDepth) {
            neckDepth = minDepth
        } else if (neckDepth > maxDepth) {
            warnings += tr("warn.neck_deep", who, cm(neckDepth), cm(maxDepth))
            neckDepth = maxDepth
        }

        val neckPath = neckPath(spec.shape, neck, neckDepth)
        val centreTop = neckPath.end
        val isOpening = (model.opening == Opening.FRONT) == isFront

        // Armhole: front is scooped more than back.
        val hollowX = shoulder.x - if (isFront) 1.8 else 1.0
        val armDrop = f.armDepth - shoulder.y
        val armhole = PathD(
            underarm,
            listOf(
                CubicTo(
                    Pt(hollowX + (bust - hollowX) * (if (isFront) 0.0 else 0.35), f.armDepth),
                    Pt(hollowX, shoulder.y + armDrop * (if (isFront) 0.55 else 0.5)),
                    shoulder,
                ),
            ),
        )

        val edges = listOf(
            Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(centreTop, hemCentre)),
            Edge(EdgeKind.HEM, PathD.line(hemCentre, sideBottom)),
            Edge(EdgeKind.SIDE, PathD.line(sideBottom, underarm)),
            Edge(EdgeKind.ARMHOLE, armhole),
            Edge(EdgeKind.SHOULDER, PathD.line(shoulder, neck)),
            Edge(EdgeKind.NECK, neckPath),
        )

        val darts = ArrayList<Dart>()
        val markings = ArrayList<Marking>()
        fun hemY(x: Double) = hemCentre.y + (sideBottom.y - hemCentre.y) * (x / sideBottom.x)

        if (isFront && model.princess) {
            val sideExcess = sideBottom.dist(underarm) - sideSeamLength(f, isFront = false)
            return draftPrincessFront(
                f, model, spec, neckPath, centreTop, isOpening, armhole, underarm, shoulder, neck, hemCentre,
                sideBottom, dartIntake, sideExcess, neckDepth, warnings,
            )
        }

        if (dartIntake > 0.3) {
            val half = dartIntake / 2
            val tipY = if (isFront) f.apexY + 2.5 else f.armDepth + 3.0
            val a = Pt(f.apexX - half, hemY(f.apexX - half))
            val b = Pt(f.apexX + half, hemY(f.apexX + half))
            darts += Dart(a, Pt(f.apexX, tipY), b)
        }

        if (isFront) {
            // Side (bust) dart takes up the extra front length so both side seams match.
            val backSide = sideSeamLength(f, isFront = false)
            val frontSide = sideBottom.dist(underarm)
            val intake = (frontSide - backSide).coerceIn(0.0, 6.0)
            if (frontSide - backSide > 6.0) {
                warnings += tr("warn.side_dart")
            }
            if (intake > 0.3) {
                val dir = (underarm - sideBottom).normalized()
                val centre = sideBottom + dir * min(5.0 + intake / 2, frontSide / 2)
                val legA = centre - dir * (intake / 2)
                val legB = centre + dir * (intake / 2)
                val apex = Pt(f.apexX, f.apexY)
                val tip = apex + (centre - apex).normalized() * 3.0
                darts += Dart(legA, tip, legB)
            }
            val apex = Pt(f.apexX, f.apexY)
            markings += Marking(apex + Pt(-0.8, 0.0), apex + Pt(0.8, 0.0), Marking.Kind.GUIDE)
            markings += Marking(apex + Pt(0.0, -0.8), apex + Pt(0.0, 0.8), Marking.Kind.GUIDE)
        }

        val grainX = bust * 0.62
        markings += Marking(
            Pt(grainX, f.armDepth + 1.0),
            Pt(grainX, hemY(grainX) - 3.0),
            Marking.Kind.GRAIN,
        )

        val notches = ArrayList<Notch>()
        armhole.pointAtDistance(ARMHOLE_NOTCH_FROM_UNDERARM).let { (p, t) ->
            notches += Notch(p, t, double = !isFront)
        }
        // Notch near the bottom of the side seam to line up front and back.
        PathD.line(sideBottom, underarm).pointAtDistance(3.0).let { (p, t) -> notches += Notch(p, t) }

        val name = if (isFront) tr("piece.front") else tr("piece.back")
        val cut = if (isOpening) CutInstruction(2, onFold = false) else CutInstruction(1, onFold = true)
        val notes = buildList {
            add(if (isOpening) tr("note.opening", model.opening.label, cm(2.5)) else tr("note.fold"))
            add(tr("note.neck", spec.shape.label, cm(neckDepth)))
            if (darts.isNotEmpty()) add(tr("note.darts", darts.joinToString(" + ") { cm(it.intake) }))
        }

        return listOf(withOutwardNotches(Piece(
            id = if (isFront) "front" else "back",
            name = name,
            cut = cut,
            edges = edges,
            darts = darts,
            markings = markings,
            points = mapOf("apex" to Pt(f.apexX, f.apexY), "underarm" to underarm, "shoulder" to shoulder, "neck" to neck),
            labelAt = Pt(bust * 0.52, f.armDepth - 3.5),
            notes = notes,
        ), notches))
    }

    /**
     * Princess-cut front: the waist dart and the bust (side) dart are replaced by a curved seam
     * that starts on the armhole, passes through the bust point and ends at the hem. The
     * waist-dart intake becomes the gap between the two curves at the hem; the extra front
     * length is taken off the side panel at the side seam so it matches the back.
     */
    private fun draftPrincessFront(
        f: BodiceFrame,
        model: BlouseModel,
        spec: NeckSpec,
        neckPath: PathD,
        centreTop: Pt,
        isOpening: Boolean,
        armhole: PathD,
        underarm: Pt,
        shoulder: Pt,
        neck: Pt,
        hemCentre: Pt,
        sideBottom: Pt,
        dartIntake: Double,
        sideExcess: Double,
        neckDepth: Double,
        warnings: MutableList<String>,
    ): List<Piece> {
        val apex = Pt(f.apexX, f.apexY)
        fun hemY(x: Double) = hemCentre.y + (sideBottom.y - hemCentre.y) * (x / sideBottom.x)

        // Princess seam leaves the armhole a little below the front hollow.
        val armCurve = armhole.segs.single() as CubicTo
        val (lowerArm, upperArm) = armCurve.splitAtLength(underarm, armhole.length() * PRINCESS_ARMHOLE_FRACTION)
        val a = lowerArm.end

        // Armhole point -> bust point, arriving vertically.
        val upper = CubicTo(
            Pt(a.x - 0.45 * (a.x - apex.x), a.y + 0.25 * (apex.y - a.y)),
            Pt(apex.x, apex.y - 0.45 * (apex.y - a.y)),
            apex,
        )
        val half = if (dartIntake > 0.3) dartIntake / 2 else 0.0
        val hemL = Pt(apex.x - half, hemY(apex.x - half))
        val hemR = Pt(apex.x + half, hemY(apex.x + half))
        fun down(to: Pt) = CubicTo(Pt(apex.x, apex.y + (to.y - apex.y) * 0.4), Pt(to.x, to.y - (to.y - apex.y) * 0.4), to)
        val lowerL = down(hemL)
        val lowerR = down(hemR)

        val excess = sideExcess.coerceIn(0.0, 6.0)
        if (sideExcess > 6.0) warnings += tr("warn.princess_long")
        val sideDir = (underarm - sideBottom).normalized()
        val sideBottomR = sideBottom + sideDir * excess

        val centreEdges = listOf(
            Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(centreTop, hemCentre)),
            Edge(EdgeKind.HEM, PathD.line(hemCentre, hemL)),
            Edge(EdgeKind.PRINCESS, PathD(hemL, listOf(lowerL.reversed(apex), upper.reversed(a)))),
            Edge(EdgeKind.ARMHOLE, PathD(a, listOf(upperArm))),
            Edge(EdgeKind.SHOULDER, PathD.line(shoulder, neck)),
            Edge(EdgeKind.NECK, neckPath),
        )
        val sideEdges = listOf(
            Edge(EdgeKind.HEM, PathD.line(hemR, sideBottomR)),
            Edge(EdgeKind.SIDE, PathD.line(sideBottomR, underarm)),
            Edge(EdgeKind.ARMHOLE, PathD(underarm, listOf(lowerArm))),
            Edge(EdgeKind.PRINCESS, PathD(a, listOf(upper, lowerR))),
        )

        // Matching notches: at the bust point and (double) halfway up the upper curve.
        val midUpper = upper.pointAt(a, 0.5)
        val midTangent = (upper.pointAt(a, 0.51) - upper.pointAt(a, 0.49)).normalized()
        val seamNotches = listOf(Notch(apex, Pt(0.0, 1.0)), Notch(midUpper, midTangent, double = true))
        val sideNotches = seamNotches.toMutableList()
        PathD(underarm, listOf(lowerArm)).pointAtDistance(ARMHOLE_NOTCH_FROM_UNDERARM).let { (p, t) -> sideNotches += Notch(p, t) }
        PathD.line(sideBottomR, underarm).pointAtDistance(3.0).let { (p, t) -> sideNotches += Notch(p, t) }

        val centreX = apex.x * 0.45
        val sideX = (hemR.x + sideBottomR.x) / 2
        val cross = listOf(
            Marking(apex + Pt(-0.8, 0.0), apex + Pt(0.8, 0.0), Marking.Kind.GUIDE),
            Marking(apex + Pt(0.0, -0.8), apex + Pt(0.0, 0.8), Marking.Kind.GUIDE),
        )
        val centre = withOutwardNotches(
            Piece(
                id = "front_centre",
                name = tr("piece.front_centre"),
                cut = if (isOpening) CutInstruction(2, onFold = false) else CutInstruction(1, onFold = true),
                edges = centreEdges,
                markings = cross + Marking(Pt(centreX, centreTop.y + 3.0), Pt(centreX, hemCentre.y - 3.0), Marking.Kind.GRAIN),
                points = mapOf("apex" to apex, "shoulder" to shoulder, "neck" to neck, "princessTop" to a),
                // Clear of the "place on fold" bracket that runs along the centre line.
                labelAt = Pt(apex.x * 0.5 + 2.0, (centreTop.y + hemCentre.y) / 2 + 7.0),
                notes = listOf(
                    if (isOpening) tr("note.opening_short", model.opening.label, cm(2.5)) else tr("note.fold"),
                    tr("note.neck", spec.shape.label, cm(neckDepth)),
                    tr("note.princess"),
                ),
            ),
            seamNotches,
        )
        val side = withOutwardNotches(
            Piece(
                id = "front_side",
                name = tr("piece.front_side"),
                cut = CutInstruction(2, onFold = false),
                edges = sideEdges,
                markings = listOf(Marking(Pt(sideX, f.armDepth + 2.0), Pt(sideX, hemY(sideX) - 3.0), Marking.Kind.GRAIN)),
                points = mapOf("apex" to apex, "underarm" to underarm, "princessTop" to a),
                labelAt = Pt(sideX, f.armDepth + 5.0),
                notes = listOf(tr("note.waist_shaping", cm(dartIntake))),
            ),
            sideNotches,
        )
        return listOf(centre, side)
    }

    private fun sideSeamLength(f: BodiceFrame, isFront: Boolean): Double {
        val bust = if (isFront) f.frontBust else f.backBust
        val waist = if (isFront) f.frontWaist else f.backWaist
        val excess = bust - waist
        val inset = if (excess <= 0) excess else {
            var s = min(excess * 0.35, 2.5)
            val maxDart = min(7.0, (f.apexX - 1.0) * 2)
            if (excess - s > maxDart) s += excess - s - maxDart
            s
        }
        val bottomY = if (isFront) max(f.frontLength, f.backLength) else f.backLength
        return Pt(bust - inset, bottomY).dist(Pt(bust, f.armDepth))
    }

    /**
     * Adds notches given as (point, tangent along the seam). The tangent is turned into the
     * normal that points out of the piece, whatever the direction the seam was travelled in.
     */
    private fun withOutwardNotches(piece: Piece, raw: List<Notch>): Piece {
        val poly = piece.seamOutline()
        val notches = raw.map { n ->
            var normal = Pt(n.outward.y, -n.outward.x)
            if (pointInPolygon(n.at + normal * 0.3, poly)) normal = -normal
            n.copy(outward = normal)
        }
        return piece.copy(notches = piece.notches + notches)
    }

    /** Neckline from the neck point [n] (on the shoulder) to the centre line at [depth]. */
    internal fun neckPath(shape: NeckShape, n: Pt, depth: Double): PathD {
        val w = n.x
        val d = depth
        val drop = d - n.y
        val segs: List<Seg> = when (shape) {
            NeckShape.ROUND -> listOf(CubicTo(Pt(w, n.y + drop * 0.55), Pt(w * 0.55, d), Pt(0.0, d)))
            NeckShape.U -> listOf(CubicTo(Pt(w, n.y + drop * 0.8), Pt(w * 0.5, d), Pt(0.0, d)))
            NeckShape.V -> listOf(
                CubicTo(Pt(w - 0.2, n.y + drop * 0.1), Pt(w * 0.2, d - drop * 0.1), Pt(0.0, d)),
            )
            NeckShape.SQUARE -> {
                val r = min(1.5, w * 0.2)
                listOf(
                    LineTo(Pt(w, d - r)),
                    CubicTo(Pt(w, d - r * 0.45), Pt(w - r * 0.45, d), Pt(w - r, d)),
                    LineTo(Pt(0.0, d)),
                )
            }
            NeckShape.BOAT -> listOf(CubicTo(Pt(w * 0.6, n.y + drop * 0.9), Pt(w * 0.3, d), Pt(0.0, d)))
            NeckShape.LEAF -> listOf(
                // Bulges out below the shoulder, then narrows to a point on the centre line.
                CubicTo(Pt(w * 1.35, n.y + drop * 0.62), Pt(w * 0.5, d - drop * 0.25), Pt(0.0, d)),
            )
            NeckShape.POT -> listOf(CubicTo(Pt(w * 1.02, n.y + drop * 0.95), Pt(w * 0.6, d), Pt(0.0, d)))
            NeckShape.SWEETHEART -> {
                val lift = min(3.0, drop * 0.25)
                val lobe = Pt(w * 0.45, d)
                listOf(
                    CubicTo(Pt(w, n.y + drop * 0.65), Pt(w * 0.85, d), lobe),
                    CubicTo(Pt(w * 0.15, d), Pt(w * 0.06, d - lift * 0.35), Pt(0.0, d - lift)),
                )
            }
        }
        return PathD(n, segs)
    }

    /** Right half of the cap from the top (0,0) down to the underarm (w, h). */
    internal fun capHalf(w: Double, h: Double): PathD {
        val mid = Pt(w * 0.5, h * 0.5)
        val dir = Pt(w, h * 1.25).normalized()
        val k = 0.3 * mid.length()
        return PathD(
            Pt.ZERO,
            listOf(
                CubicTo(Pt(w * 0.28, 0.0), mid - dir * k, mid),
                CubicTo(mid + dir * k, Pt(w * 0.82, h), Pt(w, h)),
            ),
        )
    }

    private fun draftSleeve(
        model: BlouseModel,
        m: Measurements,
        frontArm: Double,
        backArm: Double,
        warnings: MutableList<String>,
    ): List<Piece> {
        val w = (m[F.SLEEVE_ROUND] + SLEEVE_EASE) / 2
        val target = (frontArm + backArm) / 2 + CAP_EASE / 2

        var lo = 1.0
        var hi = 30.0
        val h: Double
        if (capHalf(w, lo).length() >= target) {
            h = lo
            warnings += tr("warn.arm_large")
        } else if (capHalf(w, hi).length() <= target) {
            h = hi
            warnings += tr("warn.arm_small")
        } else {
            repeat(60) {
                val mid = (lo + hi) / 2
                if (capHalf(w, mid).length() < target) lo = mid else hi = mid
            }
            h = (lo + hi) / 2
        }

        var length = when (model.sleeve) {
            SleeveStyle.CAP -> h + 4.0
            else -> m[F.SLEEVE_LENGTH]
        }
        if (length < h + 2.0) {
            warnings += tr("warn.sleeve_short", cm(h + 2.0))
            length = h + 2.0
        }
        if (model.sleeve == SleeveStyle.ELBOW && length < h + 12) {
            warnings += tr("warn.elbow_short", cm(length))
        }
        if (model.sleeve == SleeveStyle.THREE_QUARTER && length < h + 20) {
            warnings += tr("warn.threeq_short", cm(length))
        }
        var hemHalf = when (model.sleeve) {
            SleeveStyle.CAP -> w - 0.5
            else -> (m[F.SLEEVE_OPENING] + 2.0) / 2
        }
        if (hemHalf > w + 2) hemHalf = w + 2

        // Puff: taller, wider cap and a wide hem, both gathered (into the armhole / a band).
        // Bell: the hem flares out.
        val puff = model.sleeve == SleeveStyle.PUFF
        val capW = if (puff) w * PUFF_WIDTH else w
        val capH = if (puff) h + PUFF_EXTRA_CAP else h
        if (puff) hemHalf = capW
        if (model.sleeve == SleeveStyle.BELL) hemHalf = w * BELL_FLARE
        // A taller cap moves the hem down by the same amount, keeping the sleeve length.
        return listOf(sleevePiece(model, capW, capH, length + (capH - h), hemHalf, bicepHalf = w)) +
            sleeveExtras(model, m, hemHalf)
    }

    private fun sleevePiece(model: BlouseModel, w: Double, h: Double, length: Double, hemHalf: Double, bicepHalf: Double): Piece {
        val right = capHalf(w, h)
        val left = right.map(Pt::mirroredX).reversed()
        val cap = PathD(left.start, left.segs + right.segs)
        val underR = Pt(w, h)
        val underL = Pt(-w, h)
        val hemR = Pt(hemHalf, length)
        val hemL = Pt(-hemHalf, length)

        val edges = listOf(
            Edge(EdgeKind.SLEEVE_CAP, cap),
            Edge(EdgeKind.UNDERARM, PathD.line(underR, hemR)),
            Edge(EdgeKind.SLEEVE_HEM, PathD.line(hemR, hemL)),
            Edge(EdgeKind.UNDERARM, PathD.line(hemL, underL)),
        )

        // Notches that match the armhole notches (front single on the right, back double on the left).
        val notches = ArrayList<Notch>() // tangents; turned into outward normals below
        val fromUnder = ARMHOLE_NOTCH_FROM_UNDERARM + CAP_EASE / 4
        right.reversed().pointAtDistance(fromUnder).let { (p, t) -> notches += Notch(p, t) }
        left.pointAtDistance(fromUnder).let { (p, t) -> notches += Notch(p, t, double = true) }
        notches += Notch(Pt.ZERO, Pt(1.0, 0.0))

        val markings = listOf(
            Marking(Pt(w * 0.45, h * 0.62), Pt(w * 0.45, length - 1.5), Marking.Kind.GRAIN),
            Marking(underL, underR, Marking.Kind.GUIDE),
        )

        return withOutwardNotches(Piece(
            id = "sleeve",
            name = tr("piece.sleeve"),
            cut = CutInstruction(2, onFold = false),
            edges = edges,
            markings = markings,
            points = mapOf("capHeight" to Pt(0.0, h), "underarmRight" to underR, "underarmLeft" to underL),
            labelAt = Pt(-w * 0.12, h * 0.62 + min(4.0, (length - h) * 0.3)),
            notes = listOfNotNull(
                tr("note.sleeve_length", model.sleeve.label, cm(length)),
                tr("note.cap", cm(h)),
                tr("note.gather_cap").takeIf { w > bicepHalf + 0.01 },
                tr("note.gather_hem").takeIf { model.sleeve == SleeveStyle.PUFF },
            ),
        ), notches)
    }

    /** Band for puff sleeves, frills for frill sleeves. */
    private fun sleeveExtras(model: BlouseModel, m: Measurements, hemHalf: Double): List<Piece> = when (model.sleeve) {
        SleeveStyle.PUFF ->
            listOf(bandPiece("sleeve_band", tr("piece.sleeve_band"), 6.0, m[F.SLEEVE_OPENING] + 2.0, 2, tr("note.band")))
        SleeveStyle.FRILL ->
            // Two frill strips per sleeve (so each fits across folded cloth), 1.5× the hem for gathers.
            listOf(bandPiece("frill", tr("piece.frill"), 7.0, hemHalf * 2 * 1.5 / 2, 4, tr("note.frill")))
        else -> emptyList()
    }

    /**
     * A straight strip: [width] × [length] cm on the stitching line, standing upright so its
     * length runs along the grain.
     */
    internal fun bandPiece(id: String, name: String, width: Double, length: Double, count: Int, note: String): Piece {
        val a = Pt(0.0, 0.0)
        val b = Pt(width, 0.0)
        val c = Pt(width, length)
        val d = Pt(0.0, length)
        return Piece(
            id = id,
            name = name,
            cut = CutInstruction(count, onFold = false),
            edges = listOf(
                Edge(EdgeKind.BAND, PathD.line(a, b)),
                Edge(EdgeKind.BAND, PathD.line(b, c)),
                Edge(EdgeKind.BAND, PathD.line(c, d)),
                Edge(EdgeKind.BAND, PathD.line(d, a)),
            ),
            markings = listOf(Marking(Pt(width / 2, min(3.0, length * 0.2)), Pt(width / 2, length - min(3.0, length * 0.2)), Marking.Kind.GRAIN)),
            labelAt = Pt(width / 2, length / 2),
            notes = listOf(note, cm(width) + " × " + cm(length)),
        )
    }

    /** Mandarin collar: a band as long as half the neckline, cut on the fold at the closed centre. */
    private fun collarPiece(model: BlouseModel, fronts: List<Piece>, back: Piece): Piece {
        val half = fronts.sumOf { it.lengthOf(EdgeKind.NECK) } + back.lengthOf(EdgeKind.NECK)
        val height = COLLAR_HEIGHT
        val len = half + 1.0 // room to turn the ends at the opening
        val a = Pt(0.0, 0.0)
        val b = Pt(len, 0.0)
        val c = Pt(len, height)
        val d = Pt(0.0, height)
        return Piece(
            id = "collar",
            name = tr("piece.collar"),
            cut = CutInstruction(2, onFold = true),
            edges = listOf(
                Edge(EdgeKind.BAND, PathD.line(a, b)),
                Edge(EdgeKind.BAND, PathD.line(b, c)),
                Edge(EdgeKind.BAND, PathD.line(c, d)),
                Edge(EdgeKind.FOLD, PathD.line(d, a)),
            ),
            labelAt = Pt(len * 0.6, height / 2),
            notes = listOf(tr("note.collar", model.opening.label)),
        )
    }

    /** Cuts a teardrop keyhole below the back neck (back cut on the fold). */
    private fun withKeyhole(back: Piece, f: BodiceFrame, warnings: MutableList<String>): Piece {
        if (!back.hasFold) return back
        val top = back.edges.first { it.kind == EdgeKind.FOLD }.path.start.y
        val y0 = top + 2.5
        val height = min(11.0, f.backLength - 6.0 - y0)
        if (height < 5.0) {
            warnings += tr("warn.keyhole_small")
            return back
        }
        val halfW = height * 0.32
        val y1 = y0 + height
        val path = PathD(
            Pt(0.0, y0),
            listOf(
                CubicTo(Pt(halfW * 0.25, y0 + height * 0.25), Pt(halfW, y0 + height * 0.4), Pt(halfW, y0 + height * 0.65)),
                CubicTo(Pt(halfW, y0 + height * 0.9), Pt(halfW * 0.5, y1), Pt(0.0, y1)),
            ),
        )
        return back.copy(cutouts = back.cutouts + listOf(path.points()), notes = back.notes + tr("note.keyhole"))
    }

    internal fun cm(v: Double) = String.format(Locale.US, "%.1f cm", v)
}
