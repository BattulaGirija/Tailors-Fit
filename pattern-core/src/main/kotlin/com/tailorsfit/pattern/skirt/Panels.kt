package com.tailorsfit.pattern.skirt

import com.tailorsfit.pattern.geom.LineTo
import com.tailorsfit.pattern.geom.PathD
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.model.CutInstruction
import com.tailorsfit.pattern.model.Edge
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.Marking
import com.tailorsfit.pattern.model.Piece
import kotlin.math.cos
import kotlin.math.sin

/** Skirt panels: kalis (gores), circle sectors and straight panels. All cm. */
internal object Panels {
    /**
     * A symmetric panel standing upright (grain down the middle). [side] is the right-hand seam
     * from the top corner down to the hem corner; [top] and [hem] are the waist and bottom edges
     * from left to right (both default to straight lines between the corners).
     */
    fun panel(
        id: String,
        name: String,
        side: List<Pt>,
        count: Int,
        notes: List<String>,
        top: List<Pt>? = null,
        hem: List<Pt>? = null,
        sideKind: EdgeKind = EdgeKind.PRINCESS,
        markings: List<Marking> = emptyList(),
    ): Piece {
        val right = side
        val left = side.map { Pt(-it.x, it.y) }
        val topPts = top ?: listOf(left.first(), right.first())
        val hemPts = hem ?: listOf(left.last(), right.last())
        fun path(pts: List<Pt>) = PathD(pts.first(), pts.drop(1).map { LineTo(it) })
        val edges = listOf(
            Edge(sideKind, path(left)),
            Edge(EdgeKind.HEM, path(hemPts)),
            Edge(sideKind, path(right.reversed())),
            Edge(EdgeKind.BELT, path(topPts.reversed())),
        )
        val topY = topPts.maxOf { it.y }
        val bottomY = hemPts.minOf { it.y }
        val grain = Marking(Pt(0.0, topY + 3.0), Pt(0.0, bottomY - 3.0), Marking.Kind.GRAIN)
        return Piece(
            id = id,
            name = name,
            cut = CutInstruction(count, onFold = false),
            edges = edges,
            markings = listOf(grain) + markings,
            labelAt = Pt(0.0, (topY + bottomY) / 2),
            notes = notes,
        )
    }

    /**
     * A kali: [top] wide at the waist, [hip] wide [hipY] below it, flaring to [bottom] at
     * [length]. The hem is curved so it stays level once the kalis are sewn together.
     */
    fun kali(id: String, name: String, top: Double, hip: Double, hipY: Double, bottom: Double, length: Double, count: Int, notes: List<String>): Piece {
        val hipW = maxOf(hip, top + (bottom - top) * hipY / length)
        val side = listOf(
            Pt(top / 2, 0.0),
            Pt(hipW / 2, hipY),
            Pt(bottom / 2, length),
        )
        // The slanted sides make the corners longer than the middle: drop the middle to match.
        val slant = Math.hypot((bottom - top) / 2, length) - length
        val n = 12
        val hem = (0..n).map { i ->
            val t = -1.0 + 2.0 * i / n
            Pt(bottom / 2 * t, length + slant * (1 - t * t))
        }
        return panel(id, name, side, count, notes, hem = hem)
    }

    /** A sector of a circle skirt: inner radius [r] (waist), [length] long, [angle] radians wide. */
    fun sector(id: String, name: String, r: Double, length: Double, angle: Double, count: Int, notes: List<String>): Piece {
        val big = r + length
        val n = 16
        fun arc(radius: Double) = (0..n).map { i ->
            val a = -angle / 2 + angle * i / n
            Pt(radius * sin(a), radius * cos(a) - r)
        }
        val top = arc(r)
        val hem = arc(big)
        return panel(id, name, listOf(top.last(), hem.last()), count, notes, top = top, hem = hem)
    }

    /** A straight panel [width] × [length] (gathered, tiered or pleated skirts). */
    fun straight(id: String, name: String, width: Double, length: Double, count: Int, notes: List<String>, markings: List<Marking> = emptyList()): Piece =
        panel(id, name, listOf(Pt(width / 2, 0.0), Pt(width / 2, length)), count, notes, markings = markings)
}
