package com.tailorsfit.pattern.model

/** Length units shown to the tailor. Everything is stored and drafted in centimetres. */
enum class LengthUnit(val label: String, val cmPerUnit: Double) {
    CM("cm", 1.0),
    INCH("in", 2.54);

    fun toCm(value: Double) = value * cmPerUnit
    fun fromCm(cm: Double) = cm / cmPerUnit
}

/**
 * Every body measurement the app knows about. [defaultCm] is a medium size (Indian size 36),
 * [minCm]/[maxCm] are sanity limits used for validation.
 */
enum class MeasurementField(
    val key: String,
    val label: String,
    val help: String,
    val defaultCm: Double,
    val minCm: Double,
    val maxCm: Double,
) {
    BUST("bust", "Bust (chest round)", "Full round over the fullest part of the bust.", 91.5, 60.0, 160.0),
    WAIST("waist", "Waist round", "Round at the blouse bottom, just under the bust.", 76.0, 50.0, 150.0),
    SHOULDER("shoulder", "Shoulder width", "Across the back, shoulder tip to shoulder tip.", 36.0, 28.0, 50.0),
    FRONT_LENGTH("front_length", "Front length", "Shoulder (next to neck) over the bust point down to the blouse bottom.", 38.0, 28.0, 60.0),
    BACK_LENGTH("back_length", "Back length", "Shoulder (next to neck) straight down the back to the blouse bottom.", 35.5, 25.0, 55.0),
    ARMHOLE("armhole", "Armhole round", "Round the arm joint, over the shoulder tip and under the arm.", 40.5, 30.0, 60.0),
    APEX_LENGTH("apex_length", "Apex (bust point) length", "Shoulder (next to neck) to the bust point.", 25.5, 18.0, 40.0),
    APEX_TO_APEX("apex_to_apex", "Apex to apex", "Distance between the two bust points.", 18.0, 12.0, 28.0),
    FRONT_NECK_DEPTH("front_neck_depth", "Front neck depth", "Shoulder (next to neck) down to the desired front neck line.", 16.5, 5.0, 35.0),
    BACK_NECK_DEPTH("back_neck_depth", "Back neck depth", "Shoulder (next to neck) down to the desired back neck line.", 15.0, 3.0, 40.0),
    SLEEVE_LENGTH("sleeve_length", "Sleeve length", "Shoulder tip down the arm to where the sleeve should end.", 15.0, 5.0, 65.0),
    SLEEVE_ROUND("sleeve_round", "Arm round (biceps)", "Round the fullest part of the upper arm.", 30.5, 20.0, 55.0),
    SLEEVE_OPENING("sleeve_opening", "Sleeve opening round", "Round the arm where the sleeve ends.", 28.0, 15.0, 50.0);

    fun validate(cm: Double): String? = when {
        cm.isNaN() -> "$label is required"
        cm < minCm -> "$label looks too small (min ${fmt(minCm)} cm)"
        cm > maxCm -> "$label looks too large (max ${fmt(maxCm)} cm)"
        else -> null
    }

    companion object {
        fun byKey(key: String): MeasurementField? = entries.firstOrNull { it.key == key }
    }
}

internal fun fmt(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

/** Immutable set of measurements in centimetres. Missing values fall back to nothing (NaN). */
class Measurements(values: Map<MeasurementField, Double>) {
    private val values: Map<MeasurementField, Double> = values.toMap()

    operator fun get(f: MeasurementField): Double = values[f] ?: Double.NaN
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
        fun defaults(fields: Collection<MeasurementField> = MeasurementField.entries): Measurements =
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
            MeasurementField.FRONT_LENGTH to 1.0,
            MeasurementField.BACK_LENGTH to 0.5,
            MeasurementField.ARMHOLE to 2.0,
            MeasurementField.APEX_LENGTH to 0.7,
            MeasurementField.APEX_TO_APEX to 0.6,
            MeasurementField.FRONT_NECK_DEPTH to 0.3,
            MeasurementField.BACK_NECK_DEPTH to 0.3,
            MeasurementField.SLEEVE_LENGTH to 0.0,
            MeasurementField.SLEEVE_ROUND to 2.0,
            MeasurementField.SLEEVE_OPENING to 1.5,
        )
        return Measurements(MeasurementField.entries.associateWith { f -> f.defaultCm + (grade[f] ?: 0.0) * step })
    }
}
