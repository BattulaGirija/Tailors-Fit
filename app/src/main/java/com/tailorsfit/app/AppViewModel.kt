package com.tailorsfit.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.tailorsfit.app.data.Calibration
import com.tailorsfit.app.data.Customer
import com.tailorsfit.app.data.CustomerRepository
import com.tailorsfit.app.data.Settings
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.layout.LayoutEngine
import com.tailorsfit.pattern.layout.LayoutOptions
import com.tailorsfit.pattern.model.Catalog
import com.tailorsfit.pattern.model.DraftOptions
import com.tailorsfit.pattern.model.GarmentModel
import com.tailorsfit.pattern.model.LengthUnit
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.model.SeamAllowances
import com.tailorsfit.pattern.model.SizePreset
import java.util.Locale

/** Result of drafting: either a pattern with its layout, or the problems to fix first. */
sealed interface DraftResult {
    data class Ok(val pattern: Pattern, val layout: Layout) : DraftResult
    data class Invalid(val errors: Map<MeasurementField, String>) : DraftResult
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CustomerRepository(app)
    val settings = Settings(app)

    var customers by mutableStateOf(repo.loadAll())
        private set

    /** Customer currently being measured (null = new, unsaved). */
    var customerId by mutableStateOf<String?>(null)
        private set
    var customerName by mutableStateOf("")
    var customerPhone by mutableStateOf("")

    var unit by mutableStateOf(settings.unit)
        private set

    /** Text typed in each measurement box, in [unit]. Kept as text so partial input survives. */
    val inputs = mutableStateMapOf<MeasurementField, String>()

    var fabricWidthCm by mutableStateOf(settings.fabricWidthCm)
        private set
    var foldedCloth by mutableStateOf(settings.foldedCloth)
        private set
    var showAllowance by mutableStateOf(settings.showAllowance)
        private set

    // Projector state, shared between the phone controls and the external display.
    var projectorPan by mutableStateOf(Pt(-2.0, -2.0))
    var projectorGrid by mutableStateOf(false)
    var projectorCalibrating by mutableStateOf(false)
    var projectorLineWidth by mutableStateOf(settings.projectorLineWidthPx)
        private set

    init {
        fillFrom(SizePreset.M.measurements())
    }

    fun model(id: String): GarmentModel? = Catalog.model(id)

    fun changeUnit(newUnit: LengthUnit) {
        if (newUnit == unit) return
        val cm = currentMeasurements()
        unit = newUnit
        settings.unit = newUnit
        fillFrom(cm)
    }

    fun fillFrom(m: Measurements) {
        inputs.clear()
        for ((f, v) in m.asMap()) {
            if (v.isNaN()) continue
            val shown = unit.fromCm(v)
            // Tailors measure in quarter inches / half centimetres.
            val rounded = if (unit == LengthUnit.INCH) Math.round(shown * 4) / 4.0 else Math.round(shown * 2) / 2.0
            inputs[f] = format(rounded)
        }
    }

    fun applyPreset(p: SizePreset) {
        val keepNeck = currentMeasurements()
        var m = p.measurements()
        // Neck depths and sleeve length are style choices, keep what the tailor typed.
        for (f in listOf(MeasurementField.FRONT_NECK_DEPTH, MeasurementField.BACK_NECK_DEPTH, MeasurementField.SLEEVE_LENGTH)) {
            if (keepNeck.has(f)) m = m.with(f, keepNeck[f])
        }
        fillFrom(m)
    }

    /** Measurements in cm parsed from the text boxes; unparseable boxes become NaN. */
    fun currentMeasurements(): Measurements = Measurements(
        inputs.mapValues { (_, text) ->
            text.replace(',', '.').trim().toDoubleOrNull()?.let { unit.toCm(it) } ?: Double.NaN
        },
    )

    fun startNewCustomer() {
        customerId = null
        customerName = ""
        customerPhone = ""
        fillFrom(SizePreset.M.measurements())
    }

    fun selectCustomer(c: Customer) {
        customerId = c.id
        customerName = c.name
        customerPhone = c.phone
        fillFrom(Measurements.defaults().let { d -> Measurements(d.asMap() + c.measurements.asMap()) })
    }

    /** Saves the current measurements under the current customer name. Returns an error or null. */
    fun saveCustomer(): String? {
        if (customerName.isBlank()) return "Enter the customer's name first"
        val existing = customers.firstOrNull { it.id == customerId }
        val c = (existing ?: Customer(name = customerName, measurements = Measurements(emptyMap()))).copy(
            name = customerName.trim(),
            phone = customerPhone.trim(),
            measurements = Measurements(currentMeasurements().asMap().filterValues { !it.isNaN() }),
        )
        customers = repo.save(c)
        customerId = c.id
        return null
    }

    fun deleteCustomer(id: String) {
        customers = repo.delete(id)
        if (customerId == id) customerId = null
    }

    fun setFabricWidth(cm: Double) {
        fabricWidthCm = cm
        settings.fabricWidthCm = cm
    }

    fun setFolded(v: Boolean) {
        foldedCloth = v
        settings.foldedCloth = v
    }

    fun setAllowance(v: Boolean) {
        showAllowance = v
        settings.showAllowance = v
    }

    fun setLineWidth(px: Float) {
        projectorLineWidth = px
        settings.projectorLineWidthPx = px
    }

    val allowances: SeamAllowances get() = SeamAllowances()

    fun draft(model: GarmentModel): DraftResult {
        val m = currentMeasurements()
        val errors = m.validate(model.requiredMeasurements)
        if (errors.isNotEmpty()) return DraftResult.Invalid(errors)
        return try {
            val pattern = model.draft(m, DraftOptions(customerName = customerName.trim()))
            val layout = LayoutEngine.layout(
                pattern,
                LayoutOptions(
                    fabricWidth = fabricWidthCm,
                    folded = foldedCloth,
                    allowances = if (showAllowance) allowances else SeamAllowances.NONE,
                ),
            )
            DraftResult.Ok(pattern, layout)
        } catch (e: com.tailorsfit.pattern.blouse.InvalidMeasurementsException) {
            DraftResult.Invalid(e.errors)
        }
    }

    fun calibrationFor(displayKey: String, defaultPxPerCm: Float): Calibration =
        settings.calibration(displayKey) ?: Calibration(defaultPxPerCm, defaultPxPerCm)

    fun saveCalibration(displayKey: String, c: Calibration) = settings.saveCalibration(displayKey, c)

    fun format(v: Double): String {
        val s = String.format(Locale.US, if (unit == LengthUnit.INCH) "%.2f" else "%.1f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    fun formatCm(cm: Double): String = format(unit.fromCm(cm)) + " " + unit.label
}
