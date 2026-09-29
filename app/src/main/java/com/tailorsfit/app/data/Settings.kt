package com.tailorsfit.app.data

import android.content.Context
import com.tailorsfit.pattern.model.LengthUnit

/** Pixels per centimetre on a given display, found by projecting a square and measuring it. */
data class Calibration(val pxPerCmX: Float, val pxPerCmY: Float)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var unit: LengthUnit
        get() = runCatching { LengthUnit.valueOf(prefs.getString("unit", null) ?: "") }.getOrDefault(LengthUnit.INCH)
        set(v) = prefs.edit().putString("unit", v.name).apply()

    var fabricWidthCm: Double
        get() = prefs.getFloat("fabric_width", 110f).toDouble()
        set(v) = prefs.edit().putFloat("fabric_width", v.toFloat()).apply()

    var foldedCloth: Boolean
        get() = prefs.getBoolean("folded", true)
        set(v) = prefs.edit().putBoolean("folded", v).apply()

    var showAllowance: Boolean
        get() = prefs.getBoolean("allowance", true)
        set(v) = prefs.edit().putBoolean("allowance", v).apply()

    var allowTurning: Boolean
        get() = prefs.getBoolean("allow_turning", true)
        set(v) = prefs.edit().putBoolean("allow_turning", v).apply()

    /** Id of the tailor who stays logged in, or null. */
    var sessionUserId: String?
        get() = prefs.getString("session_user", null)
        set(v) = prefs.edit().putString("session_user", v).apply()

    var projectorLineWidthPx: Float
        get() = prefs.getFloat("projector_line", 3f)
        set(v) = prefs.edit().putFloat("projector_line", v).apply()

    /** Calibration for the display named [displayKey], or null if never calibrated. */
    fun calibration(displayKey: String): Calibration? {
        val x = prefs.getFloat("calib_x_$displayKey", -1f)
        val y = prefs.getFloat("calib_y_$displayKey", -1f)
        return if (x > 0 && y > 0) Calibration(x, y) else null
    }

    fun saveCalibration(displayKey: String, c: Calibration) {
        prefs.edit().putFloat("calib_x_$displayKey", c.pxPerCmX).putFloat("calib_y_$displayKey", c.pxPerCmY).apply()
    }
}
