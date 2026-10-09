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
import com.tailorsfit.pattern.model.Lengths
import com.tailorsfit.pattern.model.SeamAllowances
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class InvalidMeasurementsException(val errors: Map<F, String>) :
    IllegalArgumentException(errors.values.joinToString("; "))

/**
 * Drafts a fitted saree blouse (front, back and sleeve) from body measurements, following the
 * traditional Indian method tailors draft in inches:
 *
 * - chest and waist: a quarter of the measurement + 1" on each of front and back;
 * - armhole: half the armhole round, laid as a slant from the shoulder tip down to the chest line;
 * - shoulder: shoulder / 2 from the centre with a ½" slope;
 * - front: side dart up to 1¼" and a dart under the bust; any extra front length lifts the
 *   bottom of the front at the side (the front bottom curves down towards the centre);
 * - sleeve: cap curve as long as the armhole it is sewn into, so it sets in without puckers.
 *
 * Coordinate system for the bodice halves: x = 0 is the centre front / centre back line and x
 * grows towards the side seam; y = 0 is the level of the shoulder next to the neck (HPS) and y
 * grows downwards. All values are centimetres on the sewing line; seam allowances are added
 * later from [com.tailorsfit.pattern.model.SeamAllowances].
 */
object BlouseDrafter {
    const val INCH = 2.54
    /** Shoulder drop from the neck point to the shoulder tip. */
    const val SHOULDER_SLOPE = 0.5 * INCH
    /** Total ease at bust and waist: 1" on each quarter. */
    const val BUST_EASE = 4 * INCH
    const val WAIST_EASE = 4 * INCH
    /** Ease round the arm at the underarm line; a roomier sleeve also gets a lower cap. */
    const val SLEEVE_EASE = 2 * INCH
    /** Ease at the sleeve hem and in the puff sleeve band. */
    const val SLEEVE_HEM_EASE = 0.75 * INCH
    /** Armhole drafted this much bigger than the arm round (none: front + back curves = the arm round, as tailors draft it). */
    const val ARMHOLE_EASE = 0.0
    /** Largest side dart; extra front length is taken by lifting the front bottom at the side. */
    const val MAX_SIDE_DART = 1.25 * INCH
    /** Largest dart under the bust (bottom dart). */
    const val MAX_WAIST_DART = 1.75 * INCH
    /** Narrowest shoulder strap left beside a wide neck. */
    const val MIN_STRAP = 1.25 * INCH
    /**
     * Sleeve cap length compared with the blouse armhole: ½" longer, eased into the armhole,
     * which gives the low cap of a blouse sleeve (about 4").
     */
    const val CAP_EASE = 0.5 * INCH
    /** Distance of the balance notches from the underarm, measured along the seam. */
    const val ARMHOLE_NOTCH_FROM_UNDERARM = 7.0
    /** Puff sleeves: cap widened by this factor and raised, gathered into the armhole. */
    const val PUFF_WIDTH = 1.35
    const val PUFF_EXTRA_CAP = 3.0
    /** Bell sleeves: hem width relative to the biceps. */
    const val BELL_FLARE = 1.6
    const val COLLAR_HEIGHT = 1.5 * INCH
    const val SLEEVE_BAND_WIDTH = 2.5 * INCH
    const val TIE_WIDTH = 1.25 * INCH
    const val TIE_LENGTH = 18 * INCH
    /** Where the princess seam meets the armhole, as a fraction of armhole length from the underarm. */
    const val PRINCESS_ARMHOLE_FRACTION = 0.5
    /** Extra scoop of the arm round in from the shoulder end (none: it leaves the shoulder straight down). */
    const val FRONT_ARM_CURVE = 0.0
    const val BACK_ARM_CURVE = 0.0
    /** 3-dart blouses: the small dart near the hooks, its distance from the centre and height. */
    const val HOOK_DART_DISTANCE = 2.25 * INCH
    const val HOOK_DART_HEIGHT = 2.5 * INCH
    /** Katori / Sabyasachi: height of the belt (patti) below the cups. */
    const val BELT_HEIGHT = 2.5 * INCH
    /** Halter strap width on the shoulder. */
    const val HALTER_STRAP = 1.75 * INCH
    /** Bottom waves: depth and (about) length of each wave. */
    const val WAVE_DEPTH = 0.4 * INCH
    const val WAVE_LENGTH = 3.0 * INCH
    /** The belt stays at least this tall at the centre front. */
    const val MIN_BELT = 1.5 * INCH
    /** Height of the patti (band) across the bottom of the front. */
    const val PATTI_HEIGHT = 1.5 * INCH
    /** Princess drafting chart: front = Length + 1½" at the centre. */
    const val PRINCESS_EXTRA_LENGTH = 1.5 * INCH
    /** Princess drafting chart: front chest line = chest / 4 + this ("extra" at the side). */
    const val PRINCESS_CHEST_EXTRA = 0.5 * INCH
    /** Princess drafting chart: the seam's left leg at the bottom is this far inside the bust point. */
    const val PRINCESS_LEG_INSIDE = 0.5 * INCH
    /** How much lower the centre of a curved front bottom is than the side. */
    const val CURVE_DROP = 1.0 * INCH
    /** Front insert: how far below the front neck its lower edge comes at the centre. */
    const val INSERT_DEPTH = 2.5 * INCH
    /** Back yoke: how much lower than at the armhole its edge comes at the centre (V, round). */
    const val YOKE_DIP = 2.0 * INCH

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
            tr("summary.armhole_depth") to cm(bodice.armDepth),
            tr("summary.neck_broad") to cm(bodice.neckBase),
            tr("summary.shoulder_drop") to cm(bodice.slope),
        )

        if (model.effectiveSleeve != SleeveStyle.SLEEVELESS) {
            val sleevePieces = draftSleeve(model.effectiveSleeve, m, frontArm, backArm, warnings)
            val sleeve = sleevePieces.first()
            pieces += sleevePieces
            summary += tr("summary.cap_height") to cm(sleeve.points.getValue("capHeight").y)
            summary += tr("summary.cap_length") to cm(sleeve.lengthOf(EdgeKind.SLEEVE_CAP))
        } else {
            warnings += tr("warn.sleeveless")
        }

        if (model.collar) pieces += collarPiece(model, fronts, back)
        if (model.backDetail == BackDetail.DORI) pieces += bandPiece("tie", tr("piece.tie"), TIE_WIDTH, TIE_LENGTH, 2, tr("note.tie"))
        if (model.backDetail == BackDetail.KEYHOLE) {
            val i = pieces.indexOfFirst { it.id == "back" }
            pieces[i] = withKeyhole(pieces[i], bodice, warnings)
        }

        if (model.hasPatti && model.body == BodyStyle.PRINCESS) princessPatti(pieces)
        if (model.hasBottomCurve) curveFrontBottom(pieces)
        if (model.frontInsert != YokeShape.NONE) frontInsert(pieces, model.frontInsert, warnings)
        if (model.backYoke != YokeShape.NONE && model.backDetail != BackDetail.KEYHOLE) backYoke(pieces, model.backYoke, warnings)

        val title = buildString {
            append(model.name)
            if (options.customerName.isNotBlank()) append(" — ").append(options.customerName)
        }
        val meta = mapOf(
            "sleeve" to model.effectiveSleeve.name,
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
        val slope: Double = SHOULDER_SLOPE,
        val frontScoop: Double = FRONT_ARM_CURVE,
        val backScoop: Double = BACK_ARM_CURVE,
        /** From the shoulder down to under the bust (katori / sabyasachi belt line), if known. */
        val chestHeight: Double? = null,
    ) {
        fun shoulderY(x: Double) = slope * (x - neckBase) / (shoulderX - neckBase)
        val neckPoint get() = Pt(neckX, shoulderY(neckX))
        val shoulderTip get() = Pt(shoulderX, slope)

        companion object {
            fun create(model: BlouseModel, m: Measurements, warnings: MutableList<String>): BodiceFrame {
                // Princess cut follows the tailor's princess drafting chart: the shoulder ends
                // 1½" inside full shoulder / 2 (5½" for a size 36), the front chest line is
                // chest / 4 + ½", the bottom is waist / 4 plus the dart, and the front is
                // Length + 1½" at the centre.
                val chart = model.body == BodyStyle.PRINCESS
                var shoulderX = m[F.SHOULDER] / 2 - (if (chart) 1.5 * INCH else 0.0)
                if (model.effectiveSleeve == SleeveStyle.SLEEVELESS) shoulderX -= 0.5 * INCH // keeps straps off the arm
                // Neck width: the tailor's "neck broad" if set, else full shoulder / 2 minus the
                // shoulder (strap) width measured on the customer.
                val strap = m[F.SHOULDER_WIDTH].takeIf { !it.isNaN() }
                val neckBase = (m.adjustment(F.NECK_BROAD)
                    ?: strap?.let { shoulderX - it }
                    ?: (m[F.SHOULDER] * 0.18)).coerceIn(1.75 * INCH, shoulderX - MIN_STRAP)
                val slope = m.adjustment(F.SHOULDER_DROP) ?: SHOULDER_SLOPE
                val frontScoop = m.adjustment(F.FRONT_ARM_CURVE) ?: FRONT_ARM_CURVE
                val backScoop = m.adjustment(F.BACK_ARM_CURVE) ?: BACK_ARM_CURVE
                val widen = max(model.front.widen, model.back.widen)
                val neckX = min(neckBase + widen, shoulderX - MIN_STRAP).coerceAtLeast(1.5 * INCH)
                // Halter: the shoulder (strap) stops a little beyond the neck; the armhole is cut in.
                if (model.halter) shoulderX = min(shoulderX, neckX + HALTER_STRAP)
                if (neckBase + widen > shoulderX - MIN_STRAP) warnings += tr("warn.neck_wide", cm(MIN_STRAP))

                // "Length" is measured straight down from the shoulder; the front also goes over
                // the bust, so it is longer by ½" plus half of what the bust is bigger than the
                // upper chest (at most 2"), unless the tailor gives the front length.
                val backLength = m[F.BACK_LENGTH]
                val upper = m[F.UPPER_CHEST].takeIf { !it.isNaN() } ?: (m[F.BUST] - 2.5)
                val frontLength = m.adjustment(F.FRONT_LENGTH)
                    ?: if (chart) backLength + PRINCESS_EXTRA_LENGTH
                    else (backLength + (0.5 * INCH + (m[F.BUST] - upper) / 2).coerceIn(0.5 * INCH, 2.0 * INCH))
                val chest = m[F.BUST] / 4 + BUST_EASE / 4
                val frontChest = if (chart) m[F.BUST] / 4 + PRINCESS_CHEST_EXTRA else chest
                // Armhole depth: deep enough that the front and back armhole curves together are
                // as long as the armhole round + 1" ease (like laying armhole / 2 as a slant from
                // the shoulder tip to the chest line).
                val shoulderTip = Pt(shoulderX, slope)
                fun armholeFor(depth: Double) =
                    armholePath(Pt(frontChest, depth), shoulderTip, true, frontScoop).length() +
                        armholePath(Pt(chest, depth), shoulderTip, false, backScoop).length()
                val targetArm = m[F.ARMHOLE] + ARMHOLE_EASE
                var lo = slope + 2.0
                var hi = slope + 40.0
                repeat(50) {
                    val mid = (lo + hi) / 2
                    if (armholeFor(mid) < targetArm) lo = mid else hi = mid
                }
                var armDepth = m.adjustment(F.ARMHOLE_DEPTH) ?: ((lo + hi) / 2)
                val maxArm = min(frontLength, backLength) - 6.0
                if (armDepth > maxArm) {
                    warnings += tr("warn.armhole_deep", cm(maxArm))
                    armDepth = maxArm
                }
                if (armDepth < slope + 8) armDepth = slope + 8

                // Bust points: half the given distance, else center chest / 10 from the centre.
                val apexX = m.adjustment(F.APEX_TO_APEX)?.div(2) ?: (m[F.BUST] / 10).coerceIn(2.75 * INCH, 5.0 * INCH)
                val dx = apexX - neckBase
                val apexLen = m[F.APEX_LENGTH]
                var apexY = if (apexLen > kotlin.math.abs(dx)) sqrt(apexLen * apexLen - dx * dx) else apexLen
                if (apexY > frontLength - 4) {
                    warnings += tr("warn.apex_low")
                    apexY = frontLength - 4
                }
                if (frontLength < backLength) {
                    warnings += tr("warn.front_short", cm(0.5 * INCH) + "–" + cm(2 * INCH))
                }
                if (m[F.WAIST] > m[F.BUST]) warnings += tr("warn.waist_big")
                if (m[F.UPPER_CHEST] > m[F.BUST] + 2.0) warnings += tr("warn.upper_chest_big")
                val chestHeight = m[F.CHEST_HEIGHT].takeIf { !it.isNaN() && it > apexY + 1.0 * INCH }
                if (!m[F.CHEST_HEIGHT].isNaN() && chestHeight == null) warnings += tr("warn.chest_height")

                return BodiceFrame(
                    shoulderX = shoulderX,
                    neckBase = neckBase,
                    neckX = neckX,
                    armDepth = armDepth,
                    frontBust = frontChest,
                    backBust = chest,
                    frontWaist = m[F.WAIST] / 4 + (if (chart) 0.0 else WAIST_EASE / 4),
                    backWaist = m[F.WAIST] / 4 + WAIST_EASE / 4,
                    apexX = apexX,
                    apexY = apexY,
                    frontLength = frontLength,
                    backLength = backLength,
                    slope = slope,
                    frontScoop = frontScoop,
                    backScoop = backScoop,
                    chestHeight = chestHeight,
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
        // The front side seam is as long as the back one plus the side dart; if the front is
        // longer still, its bottom rises towards the side (the curve of an Indian blouse front).
        val maxSideDart = m.adjustment(F.SIDE_DART_WIDTH) ?: MAX_SIDE_DART
        val sideDart = if (isFront) (f.frontLength - f.backLength).coerceIn(0.0, maxSideDart) else 0.0
        val sideBottomY = if (isFront) max(f.frontLength, f.backLength).let { min(it, f.backLength + sideDart) } else f.backLength

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
            val maxDart = min(MAX_WAIST_DART, (f.apexX - 1.0) * 2)
            if (dartIntake > maxDart) {
                sideInset += dartIntake - maxDart
                dartIntake = maxDart
            }
            // The tailor's own dart width under the bust; the side seam takes the rest.
            val wanted = if (isFront) m.adjustment(F.FRONT_DART_WIDTH) else null
            if (wanted != null) {
                dartIntake = wanted.coerceAtMost(min(excess, (f.apexX - 1.0) * 2))
                sideInset = excess - dartIntake
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

        val armhole = armholePath(underarm, shoulder, isFront, if (isFront) f.frontScoop else f.backScoop)

        // Bottom: level from the centre to under the bust point, then straight to the side.
        val hemBend = Pt(min(f.apexX, sideBottom.x * 0.6), centreLength)
        fun baseHemY(x: Double) = when {
            sideBottom.y >= centreLength - 0.05 -> hemCentre.y + (sideBottom.y - hemCentre.y) * (x / sideBottom.x)
            x <= hemBend.x -> centreLength
            else -> centreLength + (sideBottom.y - centreLength) * ((x - hemBend.x) / (sideBottom.x - hemBend.x))
        }
        // Bottom waves: scallops hanging below the bottom line, a whole number of them from the
        // centre to the side so both ends stay on the line.
        val waves = model.bottomWaves && !(isFront && (model.body.panelled || model.hasPatti))
        val waveCount = max(2, Math.round(sideBottom.x / WAVE_LENGTH).toInt())
        fun wave(x: Double) = if (!waves) 0.0 else WAVE_DEPTH * (1 - kotlin.math.cos(2 * Math.PI * waveCount * x / sideBottom.x)) / 2
        fun hemY(x: Double) = baseHemY(x) + wave(x)
        val hemPath = when {
            waves -> PathD(hemCentre, (1..waveCount * 12).map { i -> val x = sideBottom.x * i / (waveCount * 12); LineTo(if (i == waveCount * 12) sideBottom else Pt(x, hemY(x))) })
            sideBottom.y < centreLength - 0.05 -> PathD(hemCentre, listOf(LineTo(hemBend), LineTo(sideBottom)))
            else -> PathD.line(hemCentre, sideBottom)
        }
        val edges = listOf(
            Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(centreTop, hemCentre)),
            Edge(EdgeKind.HEM, hemPath),
            Edge(EdgeKind.SIDE, PathD.line(sideBottom, underarm)),
            Edge(EdgeKind.ARMHOLE, armhole),
            Edge(EdgeKind.SHOULDER, PathD.line(shoulder, neck)),
            Edge(EdgeKind.NECK, neckPath),
        )

        val darts = ArrayList<Dart>()
        val markings = ArrayList<Marking>()

        if (isFront && model.body.panelled) {
            val sideExcess = sideBottom.dist(underarm) - sideSeamLength(f, isFront = false, m)
            if (model.body.belted) {
                return draftBeltedFront(
                    f, model, spec, neckPath, centreTop, isOpening, armhole, underarm, shoulder, neck, hemCentre,
                    sideBottom, dartIntake, sideExcess, neckDepth, warnings,
                )
            }
            return draftPrincessFront(
                f, model, spec, neckPath, centreTop, isOpening, armhole, underarm, shoulder, neck, hemCentre,
                sideBottom, dartIntake, sideExcess, neckDepth, warnings,
            )
        }

        // A dart pointing up from the bottom: stops below any deep neck (e.g. a pot back) and is
        // left out if no room is left.
        fun bottomDart(x: Double, intake: Double, wantedTipY: Double) {
            val half = intake / 2
            var tipY = wantedTipY
            val neckAbove = neckPath.points().filter { kotlin.math.abs(it.x - x) <= half + 1.0 }.maxOfOrNull { it.y }
            if (neckAbove != null) tipY = max(tipY, neckAbove + 1.0 * INCH)
            val a = Pt(x - half, hemY(x - half))
            val b = Pt(x + half, hemY(x + half))
            if (min(a.y, b.y) - tipY >= 1.5 * INCH) darts += Dart(a, Pt(x, tipY), b)
        }

        if (dartIntake > 0.3) {
            // 3- and 4-dart fronts move about a third of the waist shaping into a small dart near
            // the hooks, so the dart under the bust stays slim.
            var main = dartIntake
            if (isFront && dartIntake >= 0.75 * INCH) {
                val hook = min(dartIntake * 0.35, 0.75 * INCH)
                val mainHalf = (dartIntake - hook) / 2
                val hookX = (m.adjustment(F.HOOK_DART_DISTANCE) ?: HOOK_DART_DISTANCE)
                    .coerceAtMost(f.apexX - mainHalf - hook / 2 - 1.0)
                if (hookX - hook / 2 >= 1.0) {
                    main -= hook
                    bottomDart(hookX, hook, max(centreLength - HOOK_DART_HEIGHT, f.apexY + 1.0 * INCH))
                }
            }
            bottomDart(f.apexX, main, if (isFront) f.apexY + 2.5 else f.armDepth + 3.0)
        }

        if (isFront) {
            // Side (bust) dart takes up the extra front length so both side seams match.
            val backSide = sideSeamLength(f, isFront = false, m)
            val frontSide = sideBottom.dist(underarm)
            val intake = (frontSide - backSide).coerceIn(0.0, MAX_SIDE_DART)
            if (f.frontLength - f.backLength > maxSideDart + 0.5 * INCH) {
                warnings += tr("warn.side_dart", cm(maxSideDart))
            }
            if (intake > 0.3) {
                val dir = (underarm - sideBottom).normalized()
                val apex = Pt(f.apexX, f.apexY)
                fun sideDartAt(fromBottom: Double, w: Double) {
                    val centre = sideBottom + dir * min(fromBottom + w / 2, frontSide / 2)
                    val tip = apex + (centre - apex).normalized() * 3.0
                    darts += Dart(centre - dir * (w / 2), tip, centre + dir * (w / 2))
                }
                if (model.body == BodyStyle.FOUR_DART && intake >= 0.6 * INCH && frontSide > 5.5 * INCH) {
                    // Two side darts, 1" apart, each taking half: a smoother line over the bust.
                    sideDartAt(2.0 * INCH, intake / 2)
                    sideDartAt(2.0 * INCH + intake / 2 + 1.0 * INCH, intake / 2)
                } else {
                    // About 2½" above the bottom of the side seam, pointing at the bust point.
                    sideDartAt(2.5 * INCH, intake)
                }
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
        if (isFront && model.hasPatti) {
            return pattiFront(
                f, model, spec, neckPath, centreTop, isOpening, armhole, underarm, shoulder, neck,
                hemPath, ::baseHemY, sideBottom, darts, neckDepth, cut,
            )
        }
        val notes = buildList {
            add(if (isOpening) tr("note.opening", model.opening.label, cm(SeamAllowances().opening)) else tr("note.fold"))
            if (model.halter) add(tr("note.halter"))
            if (waves) add(tr("note.waves"))
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
     * Princess front with a patti: both panels are cut along a straight line [PATTI_HEIGHT]
     * above the bottom; the band below is one piece as long as the two cut edges together.
     */
    private fun princessPatti(pieces: MutableList<Piece>) {
        val ci = pieces.indexOfFirst { it.id == "front_centre" }
        val si = pieces.indexOfFirst { it.id == "front_side" }
        if (ci < 0 || si < 0) return
        val centre = pieces[ci]
        val side = pieces[si]
        val centreEdge = centre.edges.first()
        val hemCentre = centreEdge.path.end
        val hemL = centre.edgesOf(EdgeKind.HEM).single().path.end
        val sideHem = side.edgesOf(EdgeKind.HEM).single().path
        val hemR = sideHem.start
        val sideBottom = side.edgesOf(EdgeKind.SIDE).single().path.start
        // Level across the princess seam (so both seam edges are cut at the same height), then
        // rising with the bottom towards the side.
        val y0 = hemCentre.y - PATTI_HEIGHT
        val y1 = sideBottom.y - PATTI_HEIGHT
        val xr = hemR.x + 0.5 * INCH
        fun line(x: Double) = if (x <= xr) y0 else y0 + (y1 - y0) * (x - xr) / (sideBottom.x - xr)
        val c = PieceSplit.split(centre, ::line) ?: return
        val sd = PieceSplit.split(side, ::line) ?: return
        val cSeam = c.upper.edgesOf(EdgeKind.BELT).single().path
        val sSeam = sd.upper.edgesOf(EdgeKind.BELT).single().path
        val pc = listOf(cSeam.start, cSeam.end).maxBy { it.x }
        val sPts = sSeam.points().sortedBy { it.x }
        val ps = sPts.first()
        val shift = Pt(pc.x - ps.x, 0.0)
        val top = Pt(0.0, y0)
        val topSide = sPts.map { it + shift } // from the joined seam point out to the side
        val bottom = listOf(hemCentre, hemL) + sideHem.points().map { it + shift }
        fun path(pts: List<Pt>) = PathD(pts.first(), pts.drop(1).map { LineTo(it) })
        val band = Piece(
            id = "front_patti",
            name = tr("piece.front_patti"),
            cut = centre.cut,
            edges = listOf(
                Edge(centreEdge.kind, PathD.line(top, hemCentre)),
                Edge(EdgeKind.HEM, path(bottom)),
                Edge(EdgeKind.SIDE, PathD.line(bottom.last(), topSide.last())),
                Edge(EdgeKind.BELT, path(topSide.reversed() + top)),
            ),
            labelAt = Pt(topSide.last().x * 0.45, (y0 + hemCentre.y) / 2),
            notes = listOf(tr("note.patti", cm(PATTI_HEIGHT))),
        )
        pieces[ci] = c.upper.copy(notes = c.upper.notes + tr("note.patti_seam"))
        pieces[si] = sd.upper
        pieces.add(si + 1, band)
    }

    /** Lowers the front bottom towards the centre in a smooth curve ([CURVE_DROP] at the centre). */
    private fun curveFrontBottom(pieces: MutableList<Piece>) {
        val fronts = pieces.indices.filter { pieces[it].id == "front" || pieces[it].id == "front_centre" || pieces[it].id == "front_side" }
        val w = fronts.mapNotNull { pieces[it].edgesOf(EdgeKind.SIDE).firstOrNull()?.path?.start?.x }.maxOrNull() ?: return
        fun base(x: Double) = CURVE_DROP * (1 - (x / w).coerceIn(0.0, 1.0).let { it * it })
        // Both edges of a princess seam drop by the same amount, so they stay the same length.
        val legL = pieces.firstOrNull { it.id == "front_centre" }?.edgesOf(EdgeKind.HEM)?.singleOrNull()?.path?.end?.x
        val legR = pieces.firstOrNull { it.id == "front_side" }?.edgesOf(EdgeKind.HEM)?.singleOrNull()?.path?.start?.x
        fun drop(x: Double) = if (legL != null && legR != null && x >= legL - 1e-6 && x <= legR + 1e-6) base((legL + legR) / 2) else base(x)
        fun moved(p: Pt) = Pt(p.x, p.y + drop(p.x))
        for (i in fronts) {
            val piece = pieces[i]
            val hems = piece.edges.filter { it.kind == EdgeKind.HEM }
            if (hems.size != 1) continue
            val hi = piece.edges.indexOf(hems.single())
            val hemPts = hems.single().path.points()
            val fine = hemPts.zipWithNext().flatMap { (a, b) -> (0 until 12).map { k -> a.lerp(b, k / 12.0) } } + hemPts.last()
            val newHem = fine.map(::moved)
            fun onHem(p: Pt) = hemPts.zipWithNext().any { (a, b) -> distToSegment(p, a, b) < 1e-6 }
            val edges = piece.edges.mapIndexed { k, e ->
                when {
                    k == hi -> Edge(EdgeKind.HEM, PathD(newHem.first(), newHem.drop(1).map { LineTo(it) }))
                    k == (hi - 1 + piece.edges.size) % piece.edges.size && drop(e.path.end.x) > 1e-6 ->
                        e.copy(path = PathD(e.path.start, e.path.segs + LineTo(moved(e.path.end))))
                    k == (hi + 1) % piece.edges.size && drop(e.path.start.x) > 1e-6 ->
                        e.copy(path = PathD(moved(e.path.start), listOf(LineTo(e.path.start)) + e.path.segs))
                    else -> e
                }
            }
            pieces[i] = piece.copy(
                edges = edges,
                darts = piece.darts.map { d -> if (onHem(d.legA) && onHem(d.legB)) Dart(moved(d.legA), d.tip, moved(d.legB)) else d },
                notes = piece.notes + tr("note.curve", cm(CURVE_DROP)),
            )
        }
    }

    private fun polylineLengthTo(pts: List<Pt>, target: Pt): Double {
        var d = 0.0
        for (i in 0 until pts.size - 1) {
            if (pts[i] == target) return d
            d += pts[i].dist(pts[i + 1])
        }
        return d
    }

    private fun distToSegment(p: Pt, a: Pt, b: Pt): Double {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        if (len2 < 1e-12) return p.dist(a)
        val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0.0, 1.0)
        return p.dist(a.lerp(b, t))
    }

    /** 1 at the centre (t = 0) falling to 0 at t = 1: the lower edge of a front insert. */
    private fun insertCurve(shape: YokeShape, t: Double): Double {
        val r = sqrt((1 - t * t).coerceAtLeast(0.0))
        return when (shape) {
            YokeShape.NONE, YokeShape.ROUND -> r
            YokeShape.STRAIGHT -> if (t < 0.85) 1.0 else kotlin.math.cos((t - 0.85) / 0.15 * Math.PI / 2)
            YokeShape.V -> 1 - t
            YokeShape.SCALLOP -> r * (1 - 0.12 * kotlin.math.abs(kotlin.math.sin(3 * Math.PI * t)))
            YokeShape.SWEETHEART -> r - 0.2 * kotlin.math.exp(-(t / 0.22) * (t / 0.22))
        }
    }

    /**
     * Net insert below the front neck: the centre front piece is cut along a curve from the
     * centre ([INSERT_DEPTH] below the neck) up to the shoulder just past the neck point.
     */
    private fun frontInsert(pieces: MutableList<Piece>, shape: YokeShape, warnings: MutableList<String>) {
        val i = pieces.indexOfFirst { (it.id == "front" || it.id == "front_centre") && it.edgesOf(EdgeKind.NECK).isNotEmpty() }
        if (i < 0) return
        val piece = pieces[i]
        val neckEdge = piece.edgesOf(EdgeKind.NECK).single().path
        val neckPt = neckEdge.start
        val depth = neckEdge.end.y
        val apexY = piece.points["apex"]?.y ?: return
        val shoulder = piece.points["shoulder"] ?: return
        val y0 = min(depth + INSERT_DEPTH, apexY - 1.0 * INCH)
        val topY = 0.0
        var w = neckPt.x + 1.0 * INCH
        piece.points["princessTop"]?.let { if (it.y < 1.0 * INCH) w = min(w, it.x - 0.3 * INCH) }
        w = min(w, (neckPt.x + shoulder.x) / 2 + 1.0 * INCH)
        // Clear of the princess seam.
        piece.edgesOf(EdgeKind.PRINCESS).flatMap { it.path.points() }.filter { it.y <= y0 + 1.0 * INCH }
            .minOfOrNull { it.x }?.let { w = min(w, it - 0.5 * INCH) }
        if (w <= neckPt.x + 0.25 * INCH) w = neckPt.x + 0.25 * INCH
        if (y0 < depth + 1.0 * INCH || w <= 1.0) {
            warnings += tr("warn.no_insert")
            return
        }
        // At least ¾" of net below the neckline everywhere, so the insert is one piece.
        val neckPts = neckEdge.points().sortedBy { it.x }
        fun neckY(x: Double): Double {
            for (k in 0 until neckPts.size - 1) {
                val a = neckPts[k]
                val b = neckPts[k + 1]
                if (x <= b.x) return if (b.x - a.x < 1e-9) b.y else a.y + (b.y - a.y) * ((x - a.x) / (b.x - a.x)).coerceIn(0.0, 1.0)
            }
            return neckPts.last().y
        }
        fun floor(x: Double) = if (x <= neckPt.x) neckY(x) + 0.75 * INCH else 0.75 * INCH * (w - x) / (w - neckPt.x)
        // ... and at least ½" inside a princess seam (upper part, from its top down to the bust point).
        val seamPts = piece.edgesOf(EdgeKind.PRINCESS).flatMap { it.path.points() }.filter { it.y <= apexY }.sortedBy { it.x }
        fun seamY(x: Double): Double {
            if (seamPts.size < 2 || x < seamPts.first().x) return Double.MAX_VALUE
            for (k in 0 until seamPts.size - 1) {
                val a = seamPts[k]
                val b = seamPts[k + 1]
                if (x <= b.x) return if (b.x - a.x < 1e-9) min(a.y, b.y) else a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
            }
            return seamPts.last().y
        }
        fun line(x: Double) = if (x >= w) topY - (x - w)
        else min(max(topY + (y0 - topY) * insertCurve(shape, x / w), floor(x)), seamY(x) - 0.5 * INCH)
        val split = PieceSplit.split(piece, ::line) ?: run { warnings += tr("warn.no_insert"); return }
        val insert = split.upper.copy(
            id = "front_insert",
            name = tr("piece.front_insert"),
            notes = listOf(tr("note.net", shape.label)) + piece.notes.take(1),
        )
        pieces[i] = split.lower
        pieces.add(i, insert)
    }

    /** Net yoke across the upper back, its lower edge in the given shape. */
    private fun backYoke(pieces: MutableList<Piece>, shape: YokeShape, warnings: MutableList<String>) {
        val i = pieces.indexOfFirst { it.id == "back" }
        if (i < 0) return
        val back = pieces[i]
        val depth = back.edgesOf(EdgeKind.NECK).single().path.end.y
        val underarm = back.edgesOf(EdgeKind.SIDE).single().path.end
        val length = back.edges.first().path.end.y
        val w = underarm.x
        val y0 = max(underarm.y * 0.75, depth + 1.5 * INCH)
        fun line(x: Double): Double {
            val t = (x / w).coerceIn(0.0, 1.0)
            return y0 + when (shape) {
                YokeShape.NONE, YokeShape.STRAIGHT -> 0.0
                YokeShape.V -> YOKE_DIP * (1 - t)
                YokeShape.ROUND -> YOKE_DIP * sqrt(1 - t * t)
                YokeShape.SCALLOP -> 0.75 * INCH * kotlin.math.sin(3 * Math.PI * t).let { it * it }
                YokeShape.SWEETHEART -> YOKE_DIP * sqrt(1 - t * t) - 1.0 * INCH * kotlin.math.exp(-(t / 0.2) * (t / 0.2))
            }
        }
        if (line(0.0) > length - 2.0 * INCH || y0 >= underarm.y) {
            warnings += tr("warn.no_yoke")
            return
        }
        val split = PieceSplit.split(back, ::line) ?: run { warnings += tr("warn.no_yoke"); return }
        val yoke = split.upper.copy(
            id = "back_yoke",
            name = tr("piece.back_yoke"),
            notes = listOf(tr("note.net", shape.label)) + back.notes.take(1),
        )
        pieces[i] = split.lower
        pieces.add(i, yoke)
    }

    /**
     * Front with a patti: the darted front is cut [PATTI_HEIGHT] above the bottom. The upper
     * part keeps its darts, which now end on the patti seam; the band below is one piece with
     * the bits of dart in it closed (so it is shorter than the seam by the dart widths there).
     */
    private fun pattiFront(
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
        hemPath: PathD,
        hemY: (Double) -> Double,
        sideBottom: Pt,
        darts: List<Dart>,
        neckDepth: Double,
        cut: CutInstruction,
    ): List<Piece> {
        val dir = (underarm - sideBottom).normalized()
        val sideTop = sideBottom + dir * PATTI_HEIGHT
        fun seamY(x: Double) = hemY(x) - PATTI_HEIGHT
        // The seam follows the bottom line, PATTI_HEIGHT higher, and meets the side seam at sideTop.
        val hemPts = hemPath.points()
        val seamPts = hemPts.dropLast(1).map { Pt(it.x, it.y - PATTI_HEIGHT) }.filter { it.x < sideTop.x - 0.05 } + sideTop
        fun onHem(p: Pt) = kotlin.math.abs(p.y - hemY(p.x)) < 1e-6
        val bottomDarts = darts.filter { onHem(it.legA) && onHem(it.legB) }.sortedBy { it.tip.x }
        // Where a dart leg crosses the seam (the tip is above it, the leg end below).
        fun cross(tip: Pt, leg: Pt): Pt {
            var lo = 0.0
            var hi = 1.0
            repeat(50) {
                val mid = (lo + hi) / 2
                val q = tip.lerp(leg, mid)
                if (q.y < seamY(q.x)) lo = mid else hi = mid
            }
            val q = tip.lerp(leg, hi)
            return Pt(q.x, seamY(q.x))
        }
        val upperDarts = darts.map { d -> if (d in bottomDarts) Dart(cross(d.tip, d.legA), d.tip, cross(d.tip, d.legB)) else d }
        val seamDarts = upperDarts.filter { d -> bottomDarts.any { it.tip == d.tip } }.sortedBy { it.tip.x }

        // Upper front.
        val seam = PathD(seamPts.first(), seamPts.drop(1).map { LineTo(it) })
        val upperEdges = listOf(
            Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(centreTop, seamPts.first())),
            Edge(EdgeKind.BELT, seam),
            Edge(EdgeKind.SIDE, PathD.line(sideTop, underarm)),
            Edge(EdgeKind.ARMHOLE, armhole),
            Edge(EdgeKind.SHOULDER, PathD.line(shoulder, neck)),
            Edge(EdgeKind.NECK, neckPath),
        )
        val apex = Pt(f.apexX, f.apexY)
        val grainX = f.frontBust * 0.62
        val upperNotches = ArrayList<Notch>()
        armhole.pointAtDistance(ARMHOLE_NOTCH_FROM_UNDERARM).let { (p, t) -> upperNotches += Notch(p, t) }
        val upper = withOutwardNotches(Piece(
            id = "front",
            name = tr("piece.front"),
            cut = cut,
            edges = upperEdges,
            darts = upperDarts,
            markings = listOf(
                Marking(apex + Pt(-0.8, 0.0), apex + Pt(0.8, 0.0), Marking.Kind.GUIDE),
                Marking(apex + Pt(0.0, -0.8), apex + Pt(0.0, 0.8), Marking.Kind.GUIDE),
                Marking(Pt(grainX, f.armDepth + 1.0), Pt(grainX, seamY(grainX) - 3.0), Marking.Kind.GRAIN),
            ),
            points = mapOf("apex" to apex, "underarm" to underarm, "shoulder" to shoulder, "neck" to neck),
            labelAt = Pt(f.frontBust * 0.52, f.armDepth - 3.5),
            notes = buildList {
                add(if (isOpening) tr("note.opening", model.opening.label, cm(SeamAllowances().opening)) else tr("note.fold"))
                if (model.halter) add(tr("note.halter"))
                add(tr("note.neck", spec.shape.label, cm(neckDepth)))
                if (upperDarts.isNotEmpty()) add(tr("note.darts", upperDarts.joinToString(" + ") { cm(it.intake) }))
                add(tr("note.patti_seam"))
            },
        ), upperNotches)

        // Patti: the strip between the seam and the bottom with the dart wedges taken out.
        fun closeUp(line: List<Pt>, wedges: List<Pair<Double, Double>>): List<Pt> {
            val all = (line + wedges.flatMap { (a, b) -> listOf(a, b) }.map { x -> Pt(x, Double.NaN) })
            val out = ArrayList<Pt>()
            val ys = line
            fun yAt(x: Double): Double {
                for (i in 0 until ys.size - 1) {
                    val a = ys[i]
                    val b = ys[i + 1]
                    if (x >= a.x - 1e-9 && x <= b.x + 1e-9) return if (b.x - a.x < 1e-9) a.y else a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
                }
                return ys.last().y
            }
            val xs = all.map { it.x }.distinct().sorted()
            for (x in xs) {
                if (wedges.any { (a, b) -> x > a + 1e-9 && x < b - 1e-9 }) continue
                val shift = wedges.filter { (_, b) -> b <= x + 1e-9 }.sumOf { (a, b) -> b - a }
                val p = Pt(x - shift, yAt(x))
                if (out.isEmpty() || out.last().dist(p) > 1e-6) out += p else out[out.size - 1] = Pt(p.x, (out.last().y + p.y) / 2)
            }
            return out
        }
        val top = closeUp(seamPts, seamDarts.map { it.legA.x to it.legB.x })
        val bottom = closeUp(hemPts, bottomDarts.map { it.legA.x to it.legB.x })
        val bandEdges = listOf(
            Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(top.first(), bottom.first())),
            Edge(EdgeKind.HEM, PathD(bottom.first(), bottom.drop(1).map { LineTo(it) })),
            Edge(EdgeKind.SIDE, PathD.line(bottom.last(), top.last())),
            Edge(EdgeKind.BELT, PathD(top.last(), top.dropLast(1).reversed().map { LineTo(it) })),
        )
        val midX = top.last().x * 0.5
        val band = Piece(
            id = "front_patti",
            name = tr("piece.front_patti"),
            cut = cut,
            edges = bandEdges,
            markings = listOf(
                Marking(Pt(midX - 2.0, (seamY(midX) + hemY(midX)) / 2), Pt(midX + 2.0, (seamY(midX) + hemY(midX)) / 2), Marking.Kind.GUIDE),
            ),
            labelAt = Pt(top.last().x * 0.45, (seamY(midX) + hemY(midX)) / 2),
            notes = listOf(tr("note.patti", cm(PATTI_HEIGHT))),
        )
        return listOf(upper, band)
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

        // Princess seam leaves the armhole a little below the front hollow, or (shoulder cut)
        // the middle of the shoulder.
        val fromShoulder = model.shoulderPrincess
        val armCurve = armhole.segs.single() as CubicTo
        // As on the drafting chart: the seam leaves the arm round where a 45° line up from the
        // corner (shoulder end, chest line) meets it.
        val corner = Pt(shoulder.x, underarm.y)
        val armPts = armhole.points()
        val armCross = armPts.minBy { kotlin.math.abs((it.x - corner.x) - (corner.y - it.y)) }
        val crossAt = polylineLengthTo(armPts, armCross).coerceIn(armhole.length() * 0.2, armhole.length() * 0.7)
        val (lowerArm, upperArm) = armCurve.splitAtLength(underarm, crossAt)
        val a = if (fromShoulder) neck.lerp(shoulder, 0.5) else lowerArm.end

        // Seam top -> bust point, arriving vertically.
        val upper = if (fromShoulder) CubicTo(
            Pt(a.x + 0.1 * (apex.x - a.x), a.y + 0.35 * (apex.y - a.y)),
            Pt(apex.x, apex.y - 0.4 * (apex.y - a.y)),
            apex,
        ) else CubicTo(
            // Down through the corner towards the bust point, arriving vertically.
            Pt(corner.x + (a.x - corner.x) * 0.2, corner.y + (a.y - corner.y) * 0.2),
            Pt(apex.x, apex.y - 0.45 * (apex.y - corner.y)),
            apex,
        )
        // At the bottom the left leg is ½" inside the bust point and the dart is the gap.
        val gap = if (dartIntake > 0.3) dartIntake else 0.0
        // (With a patti the bottom is cut off straight, so the legs stay even about the bust point.)
        val leftX = (if (model.hasPatti) apex.x - gap / 2 else apex.x - PRINCESS_LEG_INSIDE).coerceAtLeast(1.0 * INCH)
        val hemL = Pt(leftX, hemY(leftX))
        fun down(to: Pt) = CubicTo(Pt(apex.x, apex.y + (to.y - apex.y) * 0.4), Pt(to.x, to.y - (to.y - apex.y) * 0.4), to)
        val lowerL = down(hemL)
        // The right leg slants further out, so it ends a little higher: both seam edges are
        // then the same length.
        val wantLen = PathD(apex, listOf(lowerL)).length()
        var rLo = hemY(leftX + gap) - (if (model.hasPatti) 0.0 else 2.0)
        var rHi = hemY(leftX + gap)
        repeat(40) {
            val mid = (rLo + rHi) / 2
            if (PathD(apex, listOf(down(Pt(leftX + gap, mid)))).length() < wantLen) rLo = mid else rHi = mid
        }
        val hemR = Pt(leftX + gap, (rLo + rHi) / 2)
        val lowerR = down(hemR)

        val excess = sideExcess.coerceIn(0.0, 2.5 * INCH)
        if (sideExcess > 2.5 * INCH) warnings += tr("warn.princess_long")
        val sideDir = (underarm - sideBottom).normalized()
        val sideBottomR = sideBottom + sideDir * excess

        val centreEdges = if (fromShoulder) listOf(
            Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(centreTop, hemCentre)),
            Edge(EdgeKind.HEM, PathD.line(hemCentre, hemL)),
            Edge(EdgeKind.PRINCESS, PathD(hemL, listOf(lowerL.reversed(apex), upper.reversed(a)))),
            Edge(EdgeKind.SHOULDER, PathD.line(a, neck)),
            Edge(EdgeKind.NECK, neckPath),
        ) else listOf(
            Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(centreTop, hemCentre)),
            Edge(EdgeKind.HEM, PathD.line(hemCentre, hemL)),
            Edge(EdgeKind.PRINCESS, PathD(hemL, listOf(lowerL.reversed(apex), upper.reversed(a)))),
            Edge(EdgeKind.ARMHOLE, PathD(a, listOf(upperArm))),
            Edge(EdgeKind.SHOULDER, PathD.line(shoulder, neck)),
            Edge(EdgeKind.NECK, neckPath),
        )
        val sideEdges = if (fromShoulder) listOf(
            Edge(EdgeKind.HEM, PathD.line(hemR, sideBottomR)),
            Edge(EdgeKind.SIDE, PathD.line(sideBottomR, underarm)),
            Edge(EdgeKind.ARMHOLE, armhole),
            Edge(EdgeKind.SHOULDER, PathD.line(shoulder, a)),
            Edge(EdgeKind.PRINCESS, PathD(a, listOf(upper, lowerR))),
        ) else listOf(
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
        (if (fromShoulder) armhole else PathD(underarm, listOf(lowerArm))).pointAtDistance(ARMHOLE_NOTCH_FROM_UNDERARM).let { (p, t) -> sideNotches += Notch(p, t) }
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
                    if (isOpening) tr("note.opening_short", model.opening.label, cm(SeamAllowances().opening)) else tr("note.fold"),
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

    /**
     * Katori / Sabyasachi front: a belt (patti) across the bottom and two cup panels above it.
     * The cup seam starts on the armhole (katori) or the middle of the shoulder (sabyasachi),
     * passes through the bust point and reaches the belt; the waist shaping is the gap between
     * the cup panels at the belt line, closed again in the belt so it fits the waist. The side
     * panel is shortened at the side seam so front and back side seams match, as in princess.
     */
    private fun draftBeltedFront(
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
        val fromShoulder = model.body == BodyStyle.SABYASACHI

        val excess = sideExcess.coerceIn(0.0, 2.5 * INCH)
        if (sideExcess > 2.5 * INCH) warnings += tr("warn.princess_long")
        val sideDir = (underarm - sideBottom).normalized()
        val sideBottomR = sideBottom + sideDir * excess

        // Belt line: a level line 2½" above the centre bottom, kept below the bust and above
        // the bottom of the side seam.
        // Belt line: at the chest height (under the bust) when measured, else 2½" above the
        // centre bottom; the belt stays at least 1½" tall at the centre and ¾" at the side,
        // and the line stays below the bust.
        val yB = (f.chestHeight ?: (hemCentre.y - BELT_HEIGHT))
            .coerceAtMost(hemCentre.y - MIN_BELT).coerceAtMost(sideBottomR.y - 0.75 * INCH)
            .coerceAtLeast(apex.y + 1.0 * INCH)
        fun sideAt(y: Double): Pt {
            val t = ((y - sideBottomR.y) / (underarm.y - sideBottomR.y)).coerceIn(0.0, 1.0)
            return sideBottomR.lerp(underarm, t)
        }
        val sideTop = sideAt(yB)

        // Where the cup seam starts.
        val armCurve = armhole.segs.single() as CubicTo
        val (lowerArm, upperArm) = armCurve.splitAtLength(underarm, armhole.length() * PRINCESS_ARMHOLE_FRACTION)
        val top = if (fromShoulder) shoulder.lerp(neck, 0.5) else lowerArm.end
        val upper = if (fromShoulder) {
            CubicTo(Pt(top.x, top.y + 0.35 * (apex.y - top.y)), Pt(apex.x, apex.y - 0.4 * (apex.y - top.y)), apex)
        } else {
            CubicTo(Pt(top.x - 0.45 * (top.x - apex.x), top.y + 0.25 * (apex.y - top.y)), Pt(apex.x, apex.y - 0.45 * (apex.y - top.y)), apex)
        }
        val half = if (dartIntake > 0.3) dartIntake / 2 else 0.0
        val cupL = Pt(apex.x - half, yB)
        val cupR = Pt(apex.x + half, yB)
        fun down(to: Pt) = CubicTo(Pt(apex.x, apex.y + (to.y - apex.y) * 0.45), Pt(to.x, to.y - (to.y - apex.y) * 0.45), to)
        val lowerL = down(cupL)
        val lowerR = down(cupR)

        val centreEdges = buildList {
            add(Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(centreTop, Pt(0.0, yB))))
            add(Edge(EdgeKind.BELT, PathD.line(Pt(0.0, yB), cupL)))
            add(Edge(EdgeKind.PRINCESS, PathD(cupL, listOf(lowerL.reversed(apex), upper.reversed(top)))))
            if (fromShoulder) {
                add(Edge(EdgeKind.SHOULDER, PathD.line(top, neck)))
            } else {
                add(Edge(EdgeKind.ARMHOLE, PathD(top, listOf(upperArm))))
                add(Edge(EdgeKind.SHOULDER, PathD.line(shoulder, neck)))
            }
            add(Edge(EdgeKind.NECK, neckPath))
        }
        val sideEdges = buildList {
            add(Edge(EdgeKind.BELT, PathD.line(cupR, sideTop)))
            add(Edge(EdgeKind.SIDE, PathD.line(sideTop, underarm)))
            if (fromShoulder) {
                add(Edge(EdgeKind.ARMHOLE, armhole))
                add(Edge(EdgeKind.SHOULDER, PathD.line(shoulder, top)))
            } else {
                add(Edge(EdgeKind.ARMHOLE, PathD(underarm, listOf(lowerArm))))
            }
            add(Edge(EdgeKind.PRINCESS, PathD(top, listOf(upper, lowerR))))
        }

        // Belt: the strip below the cups with the waist shaping closed, so its top is as long as
        // the two cup bottoms together.
        val gapTop = cupR.x - cupL.x
        val beltTopRight = Pt(sideTop.x - gapTop, yB)
        val beltBottomRight = Pt(sideBottomR.x - gapTop, sideBottomR.y)
        val beltEdges = listOf(
            Edge(if (isOpening) EdgeKind.OPENING else EdgeKind.FOLD, PathD.line(Pt(0.0, yB), hemCentre)),
            Edge(EdgeKind.HEM, PathD.line(hemCentre, beltBottomRight)),
            Edge(EdgeKind.SIDE, PathD.line(beltBottomRight, beltTopRight)),
            Edge(EdgeKind.BELT, PathD.line(beltTopRight, Pt(0.0, yB))),
        )

        val midUpper = upper.pointAt(top, 0.5)
        val midTangent = (upper.pointAt(top, 0.51) - upper.pointAt(top, 0.49)).normalized()
        val seamNotches = listOf(Notch(apex, Pt(0.0, 1.0)), Notch(midUpper, midTangent, double = true))
        val cross = listOf(
            Marking(apex + Pt(-0.8, 0.0), apex + Pt(0.8, 0.0), Marking.Kind.GUIDE),
            Marking(apex + Pt(0.0, -0.8), apex + Pt(0.0, 0.8), Marking.Kind.GUIDE),
        )
        val noteKey = if (fromShoulder) "note.sabyasachi" else "note.katori"
        val centreX = apex.x * 0.45
        val centre = withOutwardNotches(
            Piece(
                id = "front_centre",
                name = tr("piece.front_cup_centre"),
                cut = if (isOpening) CutInstruction(2, onFold = false) else CutInstruction(1, onFold = true),
                edges = centreEdges,
                markings = cross + Marking(Pt(centreX, centreTop.y + 2.0), Pt(centreX, yB - 2.0), Marking.Kind.GRAIN),
                points = mapOf("apex" to apex, "shoulder" to shoulder, "neck" to neck, "princessTop" to top),
                labelAt = Pt(apex.x * 0.5 + 2.0, (centreTop.y + yB) / 2 + 3.0),
                notes = listOf(
                    if (isOpening) tr("note.opening_short", model.opening.label, cm(SeamAllowances().opening)) else tr("note.fold"),
                    tr("note.neck", spec.shape.label, cm(neckDepth)),
                    tr(noteKey),
                ),
            ),
            // Matches the belt at the cup's bottom corner.
            seamNotches + Notch(cupL, Pt(1.0, 0.0)),
        )
        val sideX = (cupR.x + sideTop.x) / 2
        val side = withOutwardNotches(
            Piece(
                id = "front_side",
                name = tr("piece.front_cup_side"),
                cut = CutInstruction(2, onFold = false),
                edges = sideEdges,
                markings = listOf(Marking(Pt(sideX, f.armDepth + 1.0), Pt(sideX, yB - 1.5), Marking.Kind.GRAIN)),
                points = mapOf("apex" to apex, "underarm" to underarm, "princessTop" to top),
                labelAt = Pt(sideX, (f.armDepth + yB) / 2),
                notes = listOf(tr(noteKey)),
            ),
            seamNotches + PathD.line(sideTop, underarm).pointAtDistance(1.5).let { (p, t) -> Notch(p, t) },
        )
        val belt = withOutwardNotches(
            Piece(
                id = "front_belt",
                name = tr("piece.front_belt"),
                cut = if (isOpening) CutInstruction(2, onFold = false) else CutInstruction(1, onFold = true),
                edges = beltEdges,
                markings = listOf(Marking(Pt(beltTopRight.x * 0.3, yB + 0.8), Pt(beltTopRight.x * 0.3, hemCentre.y - 0.8), Marking.Kind.GRAIN)),
                points = mapOf("cupJoin" to cupL),
                labelAt = Pt(beltTopRight.x * 0.6, (yB + hemCentre.y) / 2),
                notes = listOf(tr("note.belt", cm(gapTop))),
            ),
            listOf(Notch(cupL, Pt(1.0, 0.0))),
        )
        return listOf(centre, side, belt)
    }

    private fun sideSeamLength(f: BodiceFrame, isFront: Boolean, m: Measurements? = null): Double {
        val bust = if (isFront) f.frontBust else f.backBust
        val waist = if (isFront) f.frontWaist else f.backWaist
        val excess = bust - waist
        val inset = if (excess <= 0) excess else {
            var s = min(excess * 0.35, 2.5)
            val maxDart = min(MAX_WAIST_DART, (f.apexX - 1.0) * 2)
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
    internal fun withOutwardNotches(piece: Piece, raw: List<Notch>): Piece {
        val poly = piece.seamOutline()
        val notches = raw.map { n ->
            var normal = Pt(n.outward.y, -n.outward.x)
            if (pointInPolygon(n.at + normal * 0.3, poly)) normal = -normal
            n.copy(outward = normal)
        }
        return piece.copy(notches = piece.notches + notches)
    }

    /**
     * Armhole (arm round) from the underarm point up to the shoulder tip, the way tailors draw
     * it: leaving the shoulder end straight down, then curving out to meet the chest line level
     * at the underarm. The back curve is a little flatter. [scoop] (the tailor's arm-curve
     * adjustment) cuts the curve that much further in near the shoulder.
     */
    internal fun armholePath(underarm: Pt, shoulder: Pt, isFront: Boolean, scoop: Double = if (isFront) FRONT_ARM_CURVE else BACK_ARM_CURVE): PathD {
        val drop = underarm.y - shoulder.y
        val out = underarm.x - shoulder.x
        return PathD(
            underarm,
            listOf(
                CubicTo(
                    Pt(shoulder.x + out * (if (isFront) 0.25 else 0.35), underarm.y),
                    Pt(shoulder.x - scoop, shoulder.y + drop * 0.55),
                    shoulder,
                ),
            ),
        )
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
            NeckShape.POT -> {
                // Matka: the pot's narrow neck just below the shoulder, then a round belly wider
                // than the neck, closing in a round bottom on the centre line.
                val neckIn = Pt(w * 0.82, n.y + drop * 0.28)
                val belly = Pt(w * 1.28, n.y + drop * 0.66)
                listOf(
                    CubicTo(Pt(w, n.y + drop * 0.12), Pt(w * 0.82, n.y + drop * 0.16), neckIn),
                    CubicTo(Pt(w * 0.82, n.y + drop * 0.42), Pt(w * 1.28, n.y + drop * 0.44), belly),
                    CubicTo(Pt(w * 1.28, n.y + drop * 0.92), Pt(w * 0.62, d), Pt(0.0, d)),
                )
            }
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

    internal fun draftSleeve(
        sleeve: SleeveStyle,
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

        var length = when (sleeve) {
            SleeveStyle.CAP -> h + 4.0
            else -> m[F.SLEEVE_LENGTH]
        }
        if (length < h + 2.0) {
            warnings += tr("warn.sleeve_short", cm(h + 2.0))
            length = h + 2.0
        }
        if (sleeve == SleeveStyle.ELBOW && length < h + 12) {
            warnings += tr("warn.elbow_short", cm(length))
        }
        if (sleeve == SleeveStyle.THREE_QUARTER && length < h + 20) {
            warnings += tr("warn.threeq_short", cm(length))
        }
        var hemHalf = when (sleeve) {
            SleeveStyle.CAP -> w - 0.5
            else -> (m[F.SLEEVE_OPENING] + SLEEVE_HEM_EASE) / 2
        }
        if (hemHalf > w + 2) hemHalf = w + 2

        // Puff: taller, wider cap and a wide hem, both gathered (into the armhole / a band).
        // Bell: the hem flares out.
        val puff = sleeve == SleeveStyle.PUFF
        val capW = if (puff) w * PUFF_WIDTH else w
        val capH = if (puff) h + PUFF_EXTRA_CAP else h
        if (puff) hemHalf = capW
        if (sleeve == SleeveStyle.BELL) hemHalf = w * BELL_FLARE
        // A taller cap moves the hem down by the same amount, keeping the sleeve length.
        return listOf(sleevePiece(sleeve, capW, capH, length + (capH - h), hemHalf, bicepHalf = w)) +
            sleeveExtras(sleeve, m, hemHalf)
    }

    private fun sleevePiece(sleeve: SleeveStyle, w: Double, h: Double, length: Double, hemHalf: Double, bicepHalf: Double): Piece {
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
                tr("note.sleeve_length", sleeve.label, cm(length)),
                tr("note.cap", cm(h)),
                tr("note.gather_cap").takeIf { w > bicepHalf + 0.01 },
                tr("note.gather_hem").takeIf { sleeve == SleeveStyle.PUFF },
            ),
        ), notches)
    }

    /** Band for puff sleeves, frills for frill sleeves. */
    private fun sleeveExtras(sleeve: SleeveStyle, m: Measurements, hemHalf: Double): List<Piece> = when (sleeve) {
        SleeveStyle.PUFF ->
            listOf(bandPiece("sleeve_band", tr("piece.sleeve_band"), SLEEVE_BAND_WIDTH, m[F.SLEEVE_OPENING] + SLEEVE_HEM_EASE, 2, tr("note.band")))
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
        val len = half + 0.5 * INCH // room to turn the ends at the opening
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

    /** A length as written on the pattern, in the tailor's unit (inches by default). */
    internal fun cm(v: Double) = Lengths.format(v)
}
