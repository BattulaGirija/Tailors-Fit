package com.tailorsfit.pattern.blouse

import com.tailorsfit.pattern.i18n.tr
import com.tailorsfit.pattern.model.Catalog
import java.util.Locale

/** How deep the neck goes compared with the customer's neck depth measurement. */
enum class NeckDepth(val factor: Double) {
    SHALLOW(0.8), REGULAR(1.0), DEEP(1.35);

    val label: String get() = tr("depth.${name.lowercase()}")
}

/** Front neck designs a tailor can pick on their own, with the usual width and depth for each. */
enum class FrontNeck(val shape: NeckShape, val widen: Double = 0.0, val depth: Double = 1.0) {
    ROUND(NeckShape.ROUND),
    U(NeckShape.U, widen = 0.5),
    V(NeckShape.V, depth = 1.1),
    SQUARE(NeckShape.SQUARE, widen = 1.0),
    SWEETHEART(NeckShape.SWEETHEART, widen = 1.0),
    BOAT(NeckShape.BOAT, widen = 4.0, depth = 0.55),
    LEAF(NeckShape.LEAF, widen = 1.0),
    POT(NeckShape.POT, widen = 0.5, depth = 1.0),
    ;

    val label: String get() = shape.label

    fun spec(depth: NeckDepth) = NeckSpec(shape, widen, this.depth * depth.factor)
}

/** Back neck designs, including the ones with an extra detail (keyhole, dori). */
enum class BackNeck(val shape: NeckShape, val widen: Double = 0.0, val depth: Double = 1.0, val detail: BackDetail = BackDetail.NONE) {
    ROUND(NeckShape.ROUND),
    U(NeckShape.U, widen = 1.0, depth = 1.2),
    V(NeckShape.V, widen = 1.0, depth = 1.2),
    SQUARE(NeckShape.SQUARE, widen = 1.0),
    BOAT(NeckShape.BOAT, widen = 4.0, depth = 0.8),
    LEAF(NeckShape.LEAF, widen = 1.0, depth = 1.3),
    POT(NeckShape.POT, widen = 0.5, depth = 1.4),
    KEYHOLE(NeckShape.ROUND, depth = 0.6, detail = BackDetail.KEYHOLE),
    DORI(NeckShape.U, widen = 1.0, depth = 1.4, detail = BackDetail.DORI),
    ;

    val label: String get() = if (detail == BackDetail.NONE) shape.label else detail.label

    /** Keyhole and dori backs are cut in one piece on the fold, so the blouse opens at the front. */
    val needsFrontOpening: Boolean get() = detail != BackDetail.NONE

    /** A keyhole back keeps a high neck; the keyhole itself is the opening. */
    val hasDepthChoice: Boolean get() = detail != BackDetail.KEYHOLE

    fun spec(depth: NeckDepth) = NeckSpec(shape, widen, this.depth * (if (hasDepthChoice) depth.factor else 1.0))
}

/**
 * A blouse as customised by the tailor: the body (3 dart, princess, katori …), front and back
 * necks and sleeves, starting from a ready design ([baseId]). It becomes an ordinary
 * [BlouseModel] whose id encodes every choice, so saved work and every screen can open it again
 * with [Catalog.model].
 */
data class BlouseSpec(
    val baseId: String,
    val body: BodyStyle,
    val front: NeckSpec,
    val back: NeckSpec,
    val sleeve: SleeveStyle,
    val opening: Opening,
    val backDetail: BackDetail = BackDetail.NONE,
    val collar: Boolean = false,
    val halter: Boolean = false,
    val bottomWaves: Boolean = false,
    val patti: Boolean = false,
    val backYoke: YokeShape = YokeShape.NONE,
    val frontInsert: YokeShape = YokeShape.NONE,
    val bottomCurve: Boolean = false,
    val armholePrincess: Boolean = false,
) {
    /** The opening actually used: keyhole and dori backs open at the front. */
    val effectiveOpening: Opening get() = if (backDetail != BackDetail.NONE) Opening.FRONT else opening

    val id: String
        get() = listOf(
            PREFIX, baseId, body.name,
            front.shape.name, n(front.widen), n(front.depthFactor),
            back.shape.name, n(back.widen), n(back.depthFactor),
            backDetail.name, sleeve.name, effectiveOpening.name, if (collar) "1" else "0",
            if (halter) "1" else "0", if (bottomWaves) "1" else "0", if (patti) "1" else "0",
            backYoke.name, frontInsert.name, if (bottomCurve) "1" else "0", if (armholePrincess) "1" else "0",
        ).joinToString(SEP)

    fun withFront(choice: FrontNeck, depth: NeckDepth) = copy(front = choice.spec(depth).rounded(), collar = false)
    fun withBack(choice: BackNeck, depth: NeckDepth) = copy(back = choice.spec(depth).rounded(), backDetail = choice.detail, collar = false)

    fun toModel(): BlouseModel {
        val base = Catalog.model(baseId) as? BlouseModel
        val same = base != null && of(base) == this
        return BlouseModel(
            id = if (same) base!!.id else id,
            baseName = if (same) base!!.name else tr(
                "spec.name", body.label, front.shape.label,
                if (backDetail == BackDetail.NONE) back.shape.label else backDetail.label, sleeve.label,
            ),
            baseDescription = base?.description ?: "",
            front = front,
            back = back,
            sleeve = sleeve,
            opening = effectiveOpening,
            princess = body == BodyStyle.PRINCESS,
            backDetail = backDetail,
            collar = collar,
            body = body,
            halter = halter,
            bottomWaves = bottomWaves,
            patti = patti,
            backYoke = backYoke,
            frontInsert = frontInsert,
            bottomCurve = bottomCurve,
            armholePrincess = armholePrincess,
        )
    }

    companion object {
        const val PREFIX = "d"
        private const val SEP = "~"

        private fun n(v: Double) = String.format(Locale.US, "%.2f", v)

        /** The spec of a design (a ready one or an already customised one). */
        fun of(model: BlouseModel) = BlouseSpec(
            baseId = parse(model.id)?.baseId ?: model.id,
            body = model.body,
            front = model.front,
            back = model.back,
            sleeve = model.sleeve,
            opening = model.opening,
            backDetail = model.backDetail,
            collar = model.collar,
            halter = model.halter,
            bottomWaves = model.bottomWaves,
            patti = model.patti,
            backYoke = model.backYoke,
            frontInsert = model.frontInsert,
            bottomCurve = model.bottomCurve,
            armholePrincess = model.armholePrincess,
        ).let { it.copy(front = it.front.rounded(), back = it.back.rounded()) }

        private fun NeckSpec.rounded() = NeckSpec(shape, n(widen).toDouble(), n(depthFactor).toDouble())

        /** A plain starting point for "Design your own". */
        val BASIC = BlouseSpec(
            baseId = "blouse_round_classic", body = BodyStyle.THREE_DART,
            front = NeckSpec(NeckShape.ROUND), back = NeckSpec(NeckShape.ROUND),
            sleeve = SleeveStyle.SHORT, opening = Opening.FRONT,
        )

        /** Reads an id made by [id]; null if it is not one. */
        fun parse(id: String): BlouseSpec? {
            val p = id.split(SEP)
            // 13 parts before halter / bottom waves were added, 15 with them, 16 with the patti,
            // 20 with yokes, bottom curve and shoulder princess seams.
            if (p.size !in setOf(13, 15, 16, 20) || p[0] != PREFIX) return null
            return runCatching {
                BlouseSpec(
                    baseId = p[1],
                    body = BodyStyle.valueOf(p[2]),
                    front = NeckSpec(NeckShape.valueOf(p[3]), p[4].toDouble(), p[5].toDouble()),
                    back = NeckSpec(NeckShape.valueOf(p[6]), p[7].toDouble(), p[8].toDouble()),
                    backDetail = BackDetail.valueOf(p[9]),
                    sleeve = SleeveStyle.valueOf(p[10]),
                    opening = Opening.valueOf(p[11]),
                    collar = p[12] == "1",
                    halter = p.getOrNull(13) == "1",
                    bottomWaves = p.getOrNull(14) == "1",
                    patti = p.getOrNull(15) == "1",
                    backYoke = p.getOrNull(16)?.let { YokeShape.valueOf(it) } ?: YokeShape.NONE,
                    frontInsert = p.getOrNull(17)?.let { YokeShape.valueOf(it) } ?: YokeShape.NONE,
                    bottomCurve = p.getOrNull(18) == "1",
                    armholePrincess = p.getOrNull(19) == "1",
                )
            }.getOrNull()
        }
    }
}
