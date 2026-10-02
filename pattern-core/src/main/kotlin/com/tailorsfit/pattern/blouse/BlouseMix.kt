package com.tailorsfit.pattern.blouse

import com.tailorsfit.pattern.i18n.tr

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
    ;

    val label: String get() = shape.label
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
}

/**
 * A blouse put together from separate choices, the way customers order one: front neck, back
 * neck and sleeves. It becomes an ordinary [BlouseModel] whose id encodes the choices, so
 * saved work and every screen can open it again with [Catalog.model][com.tailorsfit.pattern.model.Catalog.model].
 */
data class BlouseMix(
    val front: FrontNeck = FrontNeck.ROUND,
    val frontDepth: NeckDepth = NeckDepth.REGULAR,
    val princess: Boolean = false,
    val back: BackNeck = BackNeck.ROUND,
    val backDepth: NeckDepth = NeckDepth.REGULAR,
    val sleeve: SleeveStyle = SleeveStyle.SHORT,
    val opening: Opening = Opening.BACK,
) {
    /** The opening actually used ([BackNeck.needsFrontOpening] wins). */
    val effectiveOpening: Opening get() = if (back.needsFrontOpening) Opening.FRONT else opening

    val id: String
        get() = listOf(
            PREFIX, front.name, frontDepth.name, if (princess) "P" else "D",
            back.name, backDepth.name, sleeve.name, effectiveOpening.name,
        ).joinToString(SEP)

    fun toModel(): BlouseModel {
        val bDepth = if (back.hasDepthChoice) backDepth.factor else 1.0
        return BlouseModel(
            id = id,
            baseName = tr("mix.name", front.label, back.label, sleeve.label),
            baseDescription = tr("mix.desc"),
            front = NeckSpec(front.shape, front.widen, front.depth * frontDepth.factor),
            back = NeckSpec(back.shape, back.widen, back.depth * bDepth),
            sleeve = sleeve,
            opening = effectiveOpening,
            princess = princess,
            backDetail = back.detail,
        )
    }

    companion object {
        const val PREFIX = "mix"
        private const val SEP = "-"

        /** Reads an id made by [id]; null if it is not one. */
        fun parse(id: String): BlouseMix? {
            val p = id.split(SEP)
            if (p.size != 8 || p[0] != PREFIX) return null
            return runCatching {
                BlouseMix(
                    front = FrontNeck.valueOf(p[1]),
                    frontDepth = NeckDepth.valueOf(p[2]),
                    princess = p[3] == "P",
                    back = BackNeck.valueOf(p[4]),
                    backDepth = NeckDepth.valueOf(p[5]),
                    sleeve = SleeveStyle.valueOf(p[6]),
                    opening = Opening.valueOf(p[7]),
                )
            }.getOrNull()
        }
    }
}
