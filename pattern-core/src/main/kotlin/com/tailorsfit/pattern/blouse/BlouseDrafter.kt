package com.tailorsfit.pattern.blouse

import com.tailorsfit.pattern.geom.CubicTo
import com.tailorsfit.pattern.geom.LineTo
import com.tailorsfit.pattern.geom.PathD
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Seg
import com.tailorsfit.pattern.geom.pointInPolygon
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

    fun draft(model: BlouseModel, m: Measurements, options: DraftOptions = DraftOptions()): Pattern {
        val errors = m.validate(model.requiredMeasurements)
        if (errors.isNotEmpty()) throw InvalidMeasurementsException(errors)

        val warnings = ArrayList<String>()
        val bodice = BodiceFrame.create(model, m, warnings)

        val front = draftBodiceHalf(bodice, isFront = true, model, m, warnings)
        val back = draftBodiceHalf(bodice, isFront = false, model, m, warnings)
        val pieces = mutableListOf(front, back)

        val frontArm = front.lengthOf(EdgeKind.ARMHOLE)
        val backArm = back.lengthOf(EdgeKind.ARMHOLE)
        val summary = mutableListOf(
            "Finished bust" to cm(bodice.frontBust * 2 + bodice.backBust * 2),
            "Finished waist" to cm((bodice.frontWaist + bodice.backWaist) * 2),
            "Armhole drafted (front + back)" to cm(frontArm + backArm),
            "Armhole measurement" to cm(m[F.ARMHOLE]),
        )

        if (model.sleeve != SleeveStyle.SLEEVELESS) {
            val sleeve = draftSleeve(model, m, frontArm, backArm, warnings)
            pieces += sleeve
            summary += "Sleeve cap height" to cm(sleeve.points.getValue("capHeight").y)
            summary += "Sleeve cap length" to cm(sleeve.lengthOf(EdgeKind.SLEEVE_CAP))
        } else {
            warnings += "Sleeveless: finish the armholes with bias facing (not included in the allowance)."
        }

        val title = buildString {
            append(model.name)
            if (options.customerName.isNotBlank()) append(" — ").append(options.customerName)
        }
        return Pattern(title, pieces, warnings, summary)
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
                if (neckBase + widen > shoulderX - 3.0) warnings += "Neck is very wide; shoulder strap kept at 3 cm."

                val frontLength = m[F.FRONT_LENGTH]
                val backLength = m[F.BACK_LENGTH]
                var armDepth = m[F.ARMHOLE] / 2
                val maxArm = min(frontLength, backLength) - 6.0
                if (armDepth > maxArm) {
                    warnings += "Armhole is deep compared to the blouse length; armhole depth limited to ${cm(maxArm)}."
                    armDepth = maxArm
                }
                if (armDepth < SHOULDER_SLOPE + 8) armDepth = SHOULDER_SLOPE + 8

                val apexX = m[F.APEX_TO_APEX] / 2
                val dx = apexX - neckBase
                val apexLen = m[F.APEX_LENGTH]
                var apexY = if (apexLen > kotlin.math.abs(dx)) sqrt(apexLen * apexLen - dx * dx) else apexLen
                if (apexY > frontLength - 4) {
                    warnings += "Bust point is very close to the blouse bottom; check apex length and front length."
                    apexY = frontLength - 4
                }
                if (frontLength < backLength) {
                    warnings += "Front length is shorter than back length; usually it is 2–5 cm longer. Please re-check."
                }
                if (m[F.WAIST] > m[F.BUST]) warnings += "Waist is larger than bust; the blouse will flare at the bottom."

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
    ): Piece {
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
        val who = if (isFront) "Front" else "Back"
        if (neckDepth < minDepth) {
            neckDepth = minDepth
        } else if (neckDepth > maxDepth) {
            warnings += "$who neck depth ${cm(neckDepth)} is too deep; limited to ${cm(maxDepth)}."
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
                warnings += "Front is much longer than back; side dart limited to 6 cm, ease in the rest."
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

        val name = if (isFront) "Front" else "Back"
        val cut = if (isOpening) CutInstruction(2, onFold = false) else CutInstruction(1, onFold = true)
        val notes = buildList {
            add(if (isOpening) "${model.opening.label}: ${cm(2.5)} overlap for hooks included" else "Place centre on the fold")
            add("Neck: ${spec.shape.label}, depth ${cm(neckDepth)}")
            if (darts.isNotEmpty()) add("Darts: " + darts.joinToString(" + ") { cm(it.intake) })
        }

        return withOutwardNotches(Piece(
            id = if (isFront) "front" else "back",
            name = name,
            cut = cut,
            edges = edges,
            darts = darts,
            markings = markings,
            points = mapOf("apex" to Pt(f.apexX, f.apexY), "underarm" to underarm, "shoulder" to shoulder, "neck" to neck),
            labelAt = Pt(bust * 0.52, f.armDepth - 3.5),
            notes = notes,
        ), notches)
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
    ): Piece {
        val w = (m[F.SLEEVE_ROUND] + SLEEVE_EASE) / 2
        val target = (frontArm + backArm) / 2 + CAP_EASE / 2

        var lo = 1.0
        var hi = 30.0
        val h: Double
        if (capHalf(w, lo).length() >= target) {
            h = lo
            warnings += "Arm round is large for this armhole; the sleeve cap is very flat. Check armhole and arm round."
        } else if (capHalf(w, hi).length() <= target) {
            h = hi
            warnings += "Armhole is large for this arm round; the sleeve cap is very tall."
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
            warnings += "Sleeve length is shorter than the cap height; using ${cm(h + 2.0)}."
            length = h + 2.0
        }
        if (model.sleeve == SleeveStyle.ELBOW && length < h + 12) {
            warnings += "Elbow sleeve looks short (${cm(length)}); measure from shoulder tip to elbow."
        }
        if (model.sleeve == SleeveStyle.THREE_QUARTER && length < h + 20) {
            warnings += "3/4 sleeve looks short (${cm(length)}); measure from shoulder tip to below the elbow."
        }
        var hemHalf = when (model.sleeve) {
            SleeveStyle.CAP -> w - 0.5
            else -> (m[F.SLEEVE_OPENING] + 2.0) / 2
        }
        if (hemHalf > w + 2) hemHalf = w + 2

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
            name = "Sleeve",
            cut = CutInstruction(2, onFold = false),
            edges = edges,
            markings = markings,
            points = mapOf("capHeight" to Pt(0.0, h), "underarmRight" to underR, "underarmLeft" to underL),
            labelAt = Pt(-w * 0.12, h * 0.62 + min(4.0, (length - h) * 0.3)),
            notes = listOf(
                "${model.sleeve.label}: length ${cm(length)}",
                "Cap height ${cm(h)}; front = single notch",
            ),
        ), notches)
    }

    internal fun cm(v: Double) = String.format(Locale.US, "%.1f cm", v)
}
