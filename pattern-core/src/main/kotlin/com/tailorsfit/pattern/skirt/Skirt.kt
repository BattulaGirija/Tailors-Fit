package com.tailorsfit.pattern.skirt

import com.tailorsfit.pattern.blouse.BlouseDrafter
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
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.model.Piece
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** How a lehenga or skirt is cut. */
enum class SkirtCut {
    /** Front and back panels widening from the hip, a dart each side. */
    A_LINE,
    /** Fitted, straight down from the hip. */
    PENCIL,
    /** Many kalis (gores) joined side by side: the classic lehenga. */
    KALIDAR,
    /** A full circle, cut as 16 sectors. */
    CIRCLE,
    /** Half a circle, cut as 8 sectors. */
    HALF_CIRCLE,
    /** Straight panels gathered into the waistband (ghagra). */
    GATHERED,
    /** Three gathered tiers, each fuller than the one above. */
    TIERED,
    /** Straight panels laid in box pleats. */
    PLEATED,
    /** Kalis fitted to the knee, flaring out below (fishtail). */
    MERMAID,
    ;

    val label: String get() = tr("skirt.cut.${name.lowercase()}")
}

data class SkirtModel(
    override val id: String,
    val baseName: String,
    val baseDescription: String,
    val cut: SkirtCut,
    /** Lehenga (true) or skirt; lehengas tie with a drawstring, skirts close with a zip. */
    val lehenga: Boolean,
    /** Number of kalis (kalidar, mermaid). */
    val kalis: Int = 12,
    /** Hem round as a multiple of the hip (kalidar) or panel width as a multiple of the hip (gathered). */
    val fullness: Double = 3.0,
) : GarmentModel {
    override val categoryId = "lehenga"
    override val name: String get() = if (I18n.has("model.$id.name")) tr("model.$id.name") else baseName
    override val description: String get() = if (I18n.has("model.$id.desc")) tr("model.$id.desc") else baseDescription
    override val group: String get() = if (lehenga) tr("group.lehenga") else tr("group.skirt")
    override val tags: List<String>
        get() = listOfNotNull(
            group,
            cut.label,
            tr("tag.kalis", kalis).takeIf { cut == SkirtCut.KALIDAR || cut == SkirtCut.MERMAID },
            if (lehenga) tr("tag.drawstring") else tr("tag.zip"),
        )
    override val requiredMeasurements: List<MeasurementField>
        get() = listOf(F.SKIRT_LENGTH, F.NATURAL_WAIST, F.HIP, F.WAIST_TO_HIP)

    override fun draft(measurements: Measurements, options: DraftOptions): Pattern = SkirtDrafter.draft(this, measurements, options)
}

/**
 * Skirt drafting: waist + 1" ease, hip + 2" ease, the length below a 1½" waistband. Lehengas
 * get a drawstring casing and a side placket, skirts a zip.
 */
object SkirtDrafter {
    private const val INCH = 2.54
    private const val BAND = 1.5 * INCH
    /** Widest straight panel (fits 44" / 112 cm cloth with seam allowances). */
    private const val MAX_PANEL = 100.0

    fun draft(model: SkirtModel, m: Measurements, options: DraftOptions = DraftOptions()): Pattern {
        val errors = m.validate(model.requiredMeasurements)
        if (errors.isNotEmpty()) throw com.tailorsfit.pattern.blouse.InvalidMeasurementsException(errors)
        val warnings = ArrayList<String>()
        val waist = m[F.NATURAL_WAIST] + 1.0 * INCH
        val hip = max(m[F.HIP] + 2.0 * INCH, waist)
        val hipY = m[F.WAIST_TO_HIP]
        val length = m[F.SKIRT_LENGTH] - BAND
        if (length < hipY + 4.0 * INCH) warnings += tr("warn.skirt_short")
        val k = model.kalis
        val pieces = ArrayList<Piece>()
        var hemRound: Double
        var sketchHem: Double

        fun straightPanels(id: String, name: String, total: Double, h: Double, note: String, markings: (Double) -> List<Marking> = { emptyList() }) {
            val n = max(2, ceil(total / MAX_PANEL).toInt())
            val w = total / n
            pieces += Panels.straight(id, name, w, h, n, listOf(note, tr("note.panels", n, Lengths.format(w), Lengths.format(h))), markings(w))
        }

        when (model.cut) {
            SkirtCut.A_LINE, SkirtCut.PENCIL -> {
                val flare = if (model.cut == SkirtCut.A_LINE) (length - hipY) * 0.22 else -0.5 * INCH
                pieces += fittedHalf("front", tr("piece.skirt_front"), waist / 4, hip / 4, hipY, length, flare, 0.75 * INCH, 3.5 * INCH)
                pieces += fittedHalf("back", tr("piece.skirt_back"), waist / 4, hip / 4, hipY, length, flare, 1.0 * INCH, 5.0 * INCH)
                hemRound = (hip / 4 + flare) * 4
                sketchHem = hip / 4 + flare
            }
            SkirtCut.KALIDAR -> {
                hemRound = hip * model.fullness
                pieces += Panels.kali("kali", tr("piece.kali"), waist / k, hip / k, hipY, hemRound / k, length, k, listOf(tr("note.kali", k), tr("note.kali_join")))
                sketchHem = hip / 4 + (length - hipY) * 0.55
            }
            SkirtCut.MERMAID -> {
                val kneeY = hipY + (length - hipY) * 0.5
                val knee = hip * 0.92
                hemRound = hip * model.fullness
                val bottom = hemRound / k
                val side = listOf(Pt(waist / k / 2, 0.0), Pt(hip / k / 2, hipY), Pt(knee / k / 2, kneeY), Pt(bottom / 2, length))
                val slant = Math.hypot(bottom / 2 - knee / k / 2, length - kneeY) - (length - kneeY)
                val hem = (0..12).map { i -> val t = -1.0 + i / 6.0; Pt(bottom / 2 * t, length + slant * (1 - t * t)) }
                pieces += Panels.panel("kali", tr("piece.kali"), side, k, listOf(tr("note.kali", k), tr("note.mermaid", Lengths.format(kneeY))), hem = hem)
                sketchHem = hip / 4 + (length - kneeY) * 0.5
            }
            SkirtCut.CIRCLE, SkirtCut.HALF_CIRCLE -> {
                val full = model.cut == SkirtCut.CIRCLE
                val r = if (full) waist / (2 * PI) else waist / PI
                val count = if (full) 16 else 8
                hemRound = (if (full) 2 * PI else PI) * (r + length)
                pieces += Panels.sector("sector", tr("piece.sector"), r, length, PI / 8, count, listOf(tr("note.sector", count), tr("note.kali_join")))
                sketchHem = hip / 4 + length * (if (full) 0.75 else 0.5)
            }
            SkirtCut.GATHERED -> {
                hemRound = hip * model.fullness
                straightPanels("panel", tr("piece.panel"), hemRound, length, tr("note.gather_waist", Lengths.format(waist)))
                sketchHem = hip / 4 + (length - hipY) * 0.4
            }
            SkirtCut.TIERED -> {
                val h = length / 3
                val t1 = hip * 1.3
                val t2 = t1 * 1.5
                val t3 = t2 * 1.5
                hemRound = t3
                straightPanels("tier1", tr("piece.tier", 1), t1, h, tr("note.gather_waist", Lengths.format(waist)))
                straightPanels("tier2", tr("piece.tier", 2), t2, h, tr("note.gather_tier", 1))
                straightPanels("tier3", tr("piece.tier", 3), t3, h, tr("note.gather_tier", 2))
                sketchHem = hip / 4 + length * 0.45
            }
            SkirtCut.PLEATED -> {
                // Box pleats: 3× the waist, each pleat 2" at the waist (6" of cloth).
                val total = waist * 3
                hemRound = total / 2
                val pleat = 2.0 * INCH
                straightPanels("panel", tr("piece.panel"), total, length, tr("note.pleats", Lengths.format(pleat))) { w ->
                    val lines = ArrayList<Marking>()
                    var x = -w / 2 + pleat
                    while (x < w / 2 - 1.0) {
                        lines += Marking(Pt(x, 0.0), Pt(x, min(length, 15.0)), Marking.Kind.GUIDE)
                        x += pleat
                    }
                    lines
                }
                sketchHem = hip / 4 + (length - hipY) * 0.3
            }
        }

        // Waistband: folded in half (3" cut, 1½" finished), with 2" overlap for the closure.
        pieces += BlouseDrafter.bandPiece(
            "waistband", tr("piece.waistband"), BAND * 2, waist + 2.0 * INCH, 1,
            if (model.lehenga) tr("note.drawstring") else tr("note.waistband"),
        )
        if (model.lehenga) {
            pieces += BlouseDrafter.bandPiece("placket", tr("piece.placket"), 2.0 * INCH, 9.0 * INCH, 2, tr("note.placket"))
        }

        val summary = listOf(
            tr("summary.waist") to Lengths.format(waist),
            tr("summary.hip") to Lengths.format(hip),
            tr("summary.length") to Lengths.format(length + BAND),
            tr("summary.hem_round") to Lengths.format(hemRound),
        )
        val meta = mapOf(
            "category" to "skirt",
            "cut" to model.cut.name,
            "waistHalf" to (waist / 4).toString(),
            "hipHalf" to (hip / 4).toString(),
            "hipY" to hipY.toString(),
            "hemHalf" to sketchHem.toString(),
            "length" to length.toString(),
            "band" to BAND.toString(),
            "kalis" to (if (model.cut == SkirtCut.CIRCLE) 16 else if (model.cut == SkirtCut.HALF_CIRCLE) 8 else k).toString(),
            "lehenga" to model.lehenga.toString(),
        )
        val title = model.name + if (options.customerName.isNotBlank()) " — " + options.customerName else ""
        return Pattern(title, pieces, warnings, summary, meta)
    }

    /** Front or back of an A-line / pencil skirt, half on the fold, with one dart. */
    private fun fittedHalf(id: String, name: String, waistQ: Double, hipQ: Double, hipY: Double, length: Double, flare: Double, dart: Double, dartLength: Double): Piece {
        val waistX = waistQ + dart
        val top = Pt(waistX, -0.4 * INCH) // the side is raised a little over the hip
        val hipPt = Pt(hipQ, hipY)
        val hemX = hipQ + flare
        val slant = Math.hypot(flare, length - hipY) - (length - hipY)
        val hemSide = Pt(hemX, length - slant)
        val side = PathD(
            hemSide,
            listOf(
                LineTo(hipPt),
                CubicTo(Pt(hipQ, hipY * 0.45), Pt(top.x + (hipQ - top.x) * 0.25, top.y + hipY * 0.1), top),
            ),
        )
        val hemCentre = Pt(0.0, length)
        val edges = listOf(
            Edge(EdgeKind.FOLD, PathD.line(Pt.ZERO, hemCentre)),
            Edge(EdgeKind.HEM, PathD(hemCentre, listOf(CubicTo(Pt(hemX * 0.5, length), Pt(hemX * 0.85, length - slant * 0.6), hemSide)))),
            Edge(EdgeKind.SIDE, side),
            Edge(EdgeKind.BELT, PathD.line(top, Pt.ZERO)),
        )
        val dx = waistX * 0.5
        fun waistY(x: Double) = top.y * x / waistX
        val darts = listOf(Dart(Pt(dx - dart / 2, waistY(dx - dart / 2)), Pt(dx, dartLength), Pt(dx + dart / 2, waistY(dx + dart / 2))))
        return Piece(
            id = id,
            name = name,
            cut = CutInstruction(1, onFold = true),
            edges = edges,
            darts = darts,
            markings = listOf(Marking(Pt(hipQ * 0.7, hipY), Pt(hipQ * 0.7, length - 3.0), Marking.Kind.GRAIN)),
            labelAt = Pt(hipQ * 0.45, hipY + 8.0),
            notes = listOf(tr("note.fold"), tr("note.darts", Lengths.format(dart))),
        )
    }
}

object SkirtCatalog {
    private fun s(id: String, name: String, desc: String, cut: SkirtCut, lehenga: Boolean, kalis: Int = 12, fullness: Double = 3.0) =
        SkirtModel(if (lehenga) "lehenga_$id" else "skirt_$id", name, desc, cut, lehenga, kalis, fullness)

    val models: List<SkirtModel> = listOf(
        s("kali8", "8 Kali Lehenga", "Classic kalidar lehenga with 8 kalis — light flare, easy to sew.", SkirtCut.KALIDAR, true, kalis = 8, fullness = 2.5),
        s("kali12", "12 Kali Lehenga", "Kalidar lehenga with 12 kalis and a full flare.", SkirtCut.KALIDAR, true, kalis = 12, fullness = 3.5),
        s("kali16", "16 Kali Lehenga", "Very full 16-kali lehenga for weddings.", SkirtCut.KALIDAR, true, kalis = 16, fullness = 4.5),
        s("kali24", "24 Kali Bridal Lehenga", "24 narrow kalis: the fullest bridal flare.", SkirtCut.KALIDAR, true, kalis = 24, fullness = 6.0),
        s("circle", "Circular Lehenga", "Full circle lehenga that swirls, cut as 16 sectors.", SkirtCut.CIRCLE, true),
        s("half_circle", "Half Circle Lehenga", "Half circle lehenga with a soft flare, cut as 8 sectors.", SkirtCut.HALF_CIRCLE, true),
        s("aline", "A-Line Lehenga", "Simple A-line lehenga with front and back panels.", SkirtCut.A_LINE, true),
        s("mermaid", "Mermaid (Fishtail) Lehenga", "Fitted to the knee and flaring out below, 8 kalis.", SkirtCut.MERMAID, true, kalis = 8, fullness = 3.0),
        s("ghagra", "Gathered Ghagra", "Straight panels gathered into the waist — the traditional ghagra.", SkirtCut.GATHERED, true, fullness = 3.0),
        s("tiered", "Tiered Lehenga", "Three gathered tiers, each fuller than the one above.", SkirtCut.TIERED, true),
        s("aline", "A-Line Skirt", "Front and back panels flaring from the hip, side zip.", SkirtCut.A_LINE, false),
        s("pencil", "Pencil Skirt", "Fitted straight skirt with darts and a back slit.", SkirtCut.PENCIL, false),
        s("circle", "Circle Skirt", "Full circle skirt, cut as 16 sectors.", SkirtCut.CIRCLE, false),
        s("half_circle", "Half Circle Skirt", "Half circle skirt, cut as 8 sectors.", SkirtCut.HALF_CIRCLE, false),
        s("gathered", "Gathered Skirt", "Straight panels gathered into the waistband.", SkirtCut.GATHERED, false, fullness = 2.0),
        s("tiered", "Tiered Skirt", "Three gathered tiers for a boho look.", SkirtCut.TIERED, false),
        s("pleated", "Box Pleated Skirt", "Straight panels laid in 2\" box pleats.", SkirtCut.PLEATED, false),
        s("panel6", "6 Panel Skirt", "Six gores (kalis) with a gentle flare.", SkirtCut.KALIDAR, false, kalis = 6, fullness = 1.8),
    )
}
