package com.tailorsfit.pattern.model

import com.tailorsfit.pattern.i18n.tr

/** Length units shown to the tailor. Everything is stored and drafted in centimetres. */
enum class LengthUnit(val label: String, val cmPerUnit: Double) {
    CM("cm", 1.0),
    INCH("in", 2.54);

    fun toCm(value: Double) = value * cmPerUnit
    fun fromCm(cm: Double) = cm / cmPerUnit
}

/** Lengths written on patterns and in messages, in the tailor's unit. */
object Lengths {
    const val INCH = 2.54

    /** Set by the app from the tailor's choice; tailors in India usually work in inches. */
    @Volatile
    var unit: LengthUnit = LengthUnit.INCH

    /** e.g. `9½"` (to the nearest ¼ inch, the way tailors read a tape) or `24.1 cm`. */
    fun format(cm: Double): String = if (unit == LengthUnit.INCH) inches(cm) else String.format(java.util.Locale.US, "%.1f cm", cm)

    fun inches(cm: Double): String {
        val quarters = Math.round(kotlin.math.abs(cm) / INCH * 4).toInt()
        val whole = quarters / 4
        val frac = arrayOf("", "¼", "½", "¾")[quarters % 4]
        val sign = if (cm < 0 && quarters > 0) "-" else ""
        return sign + (if (whole == 0 && frac.isNotEmpty()) frac else "$whole$frac") + "\""
    }

    /** Spacing of the projector's help grid: 2 inches or 5 cm. */
    val gridStepCm: Double get() = if (unit == LengthUnit.INCH) 2 * INCH else 5.0
}

/**
 * Every body measurement the app knows about. [defaultCm] is a medium size (Indian size 36),
 * [minCm]/[maxCm] are sanity limits used for validation.
 */
enum class MeasurementField(
    val key: String,
    val defaultCm: Double,
    val minCm: Double,
    val maxCm: Double,
    /**
     * Optional change to how the pattern is drafted (e.g. neck width, dart width). Left empty,
     * the drafter works the value out itself.
     */
    val isAdjustment: Boolean = false,
) {
    // In the order of a tailor's blouse measurement sheet.
    BACK_LENGTH("back_length", 35.5, 25.0, 55.0),
    UPPER_CHEST("upper_chest", 89.0, 60.0, 155.0),
    BUST("bust", 91.5, 60.0, 160.0),
    SHOULDER_WIDTH("shoulder_width", 7.6, 2.5, 15.0),
    SLEEVE_LENGTH("sleeve_length", 15.0, 5.0, 65.0),
    SLEEVE_OPENING("sleeve_opening", 28.0, 15.0, 50.0),
    SLEEVE_ROUND("sleeve_round", 30.5, 20.0, 55.0),
    FRONT_NECK_DEPTH("front_neck_depth", 16.5, 5.0, 35.0),
    BACK_NECK_DEPTH("back_neck_depth", 15.0, 3.0, 40.0),
    WAIST("waist", 76.0, 50.0, 150.0),
    APEX_LENGTH("apex_length", 25.5, 18.0, 40.0),
    CHEST_HEIGHT("chest_height", 33.0, 20.0, 50.0),
    SHOULDER("shoulder", 36.0, 28.0, 50.0),
    ARMHOLE("armhole", 40.5, 30.0, 60.0),

    // Kurtis, lehengas and skirts.
    NATURAL_WAIST("natural_waist", 76.0, 50.0, 150.0),
    HIP("hip", 99.0, 60.0, 170.0),
    WAIST_LENGTH("waist_length", 39.5, 28.0, 55.0),
    WAIST_TO_HIP("waist_to_hip", 20.5, 12.0, 35.0),
    KURTI_LENGTH("kurti_length", 106.5, 60.0, 150.0),
    SKIRT_LENGTH("skirt_length", 101.5, 40.0, 130.0),

    // Customization details (adjustments), in cm on the stitching line.
    FRONT_LENGTH("front_length", Double.NaN, 28.0, 60.0, isAdjustment = true),
    APEX_TO_APEX("apex_to_apex", Double.NaN, 12.0, 28.0, isAdjustment = true),
    NECK_BROAD("adj_neck_broad", Double.NaN, 1.5 * 2.54, 5.0 * 2.54, isAdjustment = true),
    SHOULDER_DROP("adj_shoulder_drop", Double.NaN, 0.0, 1.5 * 2.54, isAdjustment = true),
    ARMHOLE_DEPTH("adj_armhole_depth", Double.NaN, 4.0 * 2.54, 11.0 * 2.54, isAdjustment = true),
    FRONT_ARM_CURVE("adj_front_arm_curve", Double.NaN, 0.0, 2.0 * 2.54, isAdjustment = true),
    BACK_ARM_CURVE("adj_back_arm_curve", Double.NaN, 0.0, 1.5 * 2.54, isAdjustment = true),
    FRONT_DART_WIDTH("adj_front_dart", Double.NaN, 0.0, 2.5 * 2.54, isAdjustment = true),
    SIDE_DART_WIDTH("adj_side_dart", Double.NaN, 0.0, 2.0 * 2.54, isAdjustment = true),
    HOOK_DART_DISTANCE("adj_hook_dart_distance", Double.NaN, 1.0 * 2.54, 4.0 * 2.54, isAdjustment = true);

    /** Name shown to the tailor, in the current language. */
    val label: String get() = tr("field.$key")

    /** How to take this measurement, in the current language. */
    val help: String get() = tr("field.$key.help")

    fun validate(cm: Double): String? = when {
        cm.isNaN() -> tr("validate.required", label)
        cm < minCm -> tr("validate.small", label, Lengths.format(minCm))
        cm > maxCm -> tr("validate.large", label, Lengths.format(maxCm))
        else -> null
    }

    companion object {
        fun byKey(key: String): MeasurementField? = entries.firstOrNull { it.key == key }

        /** Body measurements (taken with the tape). */
        val body: List<MeasurementField> get() = entries.filter { !it.isAdjustment }

        /** The tailor's blouse measurement sheet. */
        val blouse: List<MeasurementField>
            get() = listOf(
                BACK_LENGTH, UPPER_CHEST, BUST, SHOULDER_WIDTH, SLEEVE_LENGTH, SLEEVE_OPENING, SLEEVE_ROUND,
                FRONT_NECK_DEPTH, BACK_NECK_DEPTH, WAIST, APEX_LENGTH, CHEST_HEIGHT, SHOULDER, ARMHOLE,
            )

        /** Optional drafting adjustments. */
        val adjustments: List<MeasurementField> get() = entries.filter { it.isAdjustment }
    }
}

internal fun fmt(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

/** Immutable set of measurements in centimetres. Missing values fall back to nothing (NaN). */
class Measurements(values: Map<MeasurementField, Double>) {
    private val values: Map<MeasurementField, Double> = values.toMap()

    operator fun get(f: MeasurementField): Double = values[f] ?: Double.NaN

    /** An adjustment the tailor typed, kept within its limits; null when left to the drafter. */
    fun adjustment(f: MeasurementField): Double? = this[f].takeIf { !it.isNaN() }?.coerceIn(f.minCm, f.maxCm)
    fun has(f: MeasurementField) = values[f]?.isNaN() == false
    fun with(f: MeasurementField, cm: Double) = Measurements(values + (f to cm))
    fun asMap(): Map<MeasurementField, Double> = values

    /** Validation errors for the given fields (empty = OK). */
    fun validate(fields: Collection<MeasurementField>): Map<MeasurementField, String> =
        fields.mapNotNull { f -> f.validate(this[f])?.let { f to it } }.toMap()

    override fun equals(other: Any?) = other is Measurements && other.values == values
    override fun hashCode() = values.hashCode()
    override fun toString() = values.entries.joinToString(prefix = "Measurements(", postfix = ")") { "${it.key.key}=${it.value}" }

    companion object {
        fun defaults(fields: Collection<MeasurementField> = MeasurementField.body): Measurements =
            Measurements(fields.associateWith { it.defaultCm })
    }
}

/** Ready-made standard sizes so a tailor can start from something close. */
enum class SizePreset(val label: String, private val bust: Double) {
    S("S (34)", 86.5),
    M("M (36)", 91.5),
    L("L (38)", 96.5),
    XL("XL (40)", 101.5),
    XXL("XXL (42)", 106.5);

    /** Grades the medium defaults proportionally to the bust. */
    fun measurements(): Measurements {
        val step = (bust - MeasurementField.BUST.defaultCm) / 5.0 // one size = 5 cm of bust
        val grade = mapOf(
            MeasurementField.BUST to 5.0,
            MeasurementField.WAIST to 5.0,
            MeasurementField.SHOULDER to 1.0,
            MeasurementField.UPPER_CHEST to 5.0,
            MeasurementField.CHEST_HEIGHT to 0.5,
            MeasurementField.BACK_LENGTH to 0.5,
            MeasurementField.ARMHOLE to 2.0,
            MeasurementField.APEX_LENGTH to 0.7,
            MeasurementField.FRONT_NECK_DEPTH to 0.3,
            MeasurementField.BACK_NECK_DEPTH to 0.3,
            MeasurementField.SLEEVE_LENGTH to 0.0,
            MeasurementField.SLEEVE_ROUND to 2.0,
            MeasurementField.SLEEVE_OPENING to 1.5,
            MeasurementField.NATURAL_WAIST to 5.0,
            MeasurementField.HIP to 5.0,
            MeasurementField.WAIST_LENGTH to 0.5,
            MeasurementField.WAIST_TO_HIP to 0.3,
            MeasurementField.KURTI_LENGTH to 0.0,
            MeasurementField.SKIRT_LENGTH to 0.0,
        )
        return Measurements(MeasurementField.body.associateWith { f -> f.defaultCm + (grade[f] ?: 0.0) * step })
    }
}
