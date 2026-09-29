package com.tailorsfit.app

import com.tailorsfit.pattern.i18n.Language
import com.tailorsfit.pattern.i18n.I18n
import com.tailorsfit.pattern.i18n.tr
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.tailorsfit.app.data.Calibration
import com.tailorsfit.app.data.Customer
import com.tailorsfit.app.data.Account
import com.tailorsfit.app.data.AccountStore
import com.tailorsfit.app.data.AuthResult
import com.tailorsfit.app.data.CustomerRepository
import com.tailorsfit.app.data.DesignStore
import com.tailorsfit.app.data.LocalAccountStore
import com.tailorsfit.app.data.Role
import com.tailorsfit.pattern.blouse.BlouseModel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Result of drafting: either a pattern with its layout, or the problems to fix first. */
sealed interface DraftResult {
    data class Ok(val pattern: Pattern, val layout: Layout) : DraftResult
    data class Invalid(val errors: Map<MeasurementField, String>) : DraftResult
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)

    /** Current language; changing it re-creates the screens (see TailorsFitApp). */
    var language by mutableStateOf(settings.language.also { I18n.language = it })
        private set

    fun changeLanguage(l: Language) {
        I18n.language = l
        settings.language = l
        language = l
        lastDraft = null
        lastDraftKey = null
    }
    val accounts: AccountStore = LocalAccountStore(app)
    val designs = DesignStore(app).also { it.apply() }

    /** Logged-in tailor (kept across app restarts), or null when logged out. */
    var currentUser by mutableStateOf(settings.sessionUserId?.let { accounts.find(it) }?.takeIf { it.role == Role.TAILOR })
        private set

    /** True only while the admin is logged in (never remembered across restarts). */
    var isAdmin by mutableStateOf(false)
        private set

    /** Bumped whenever admin data changes so admin screens re-read it. */
    var adminVersion by mutableStateOf(0)
        private set

    private var repo = CustomerRepository(app, currentUser?.id)

    var customers by mutableStateOf(repo.loadAll())
        private set

    fun signUp(name: String, shop: String, login: String, password: String): String? =
        handleAuth(accounts.signUp(name, shop, login, password))

    fun logIn(login: String, password: String): String? = handleAuth(accounts.logIn(login, password))

    private fun handleAuth(result: AuthResult): String? = when (result) {
        is AuthResult.Failure -> result.message
        is AuthResult.Success -> {
            startSession(result.account)
            null
        }
    }

    private fun startSession(account: Account?) {
        currentUser = account
        settings.sessionUserId = account?.id
        repo = CustomerRepository(getApplication(), account?.id)
        customers = repo.loadAll()
        startNewCustomer()
        lastDraft = null
        lastDraftKey = null
    }

    fun logOut() = startSession(null)

    fun adminLogIn(password: String): String? {
        val result = if (accounts.adminExists()) accounts.adminLogIn(password) else accounts.createAdmin(password)
        return when (result) {
            is AuthResult.Failure -> result.message
            is AuthResult.Success -> {
                isAdmin = true
                null
            }
        }
    }

    fun adminLogOut() {
        isAdmin = false
    }

    fun tailors(): List<Account> = accounts.tailors()

    fun tailorCustomers(id: String): List<Customer> = CustomerRepository(getApplication(), id).loadAll()

    fun deleteTailor(id: String) {
        accounts.deleteTailor(id)
        adminVersion++
    }

    fun saveDesign(model: BlouseModel) {
        val state = designs.load()
        designs.save(state.copy(custom = state.custom.filter { it.id != model.id } + model))
        adminVersion++
    }

    fun deleteDesign(id: String) {
        val state = designs.load()
        designs.save(state.copy(custom = state.custom.filter { it.id != id }, hidden = state.hidden - id))
        adminVersion++
    }

    fun setDesignHidden(id: String, hidden: Boolean) {
        val state = designs.load()
        designs.save(state.copy(hidden = if (hidden) state.hidden + id else state.hidden - id))
        adminVersion++
    }

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
    /** False for one-way prints / napped cloth: pieces may not be turned upside down. */
    var allowTurning by mutableStateOf(settings.allowTurning)
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

    /** Standard size the measurements came from; null once the tailor edits a value (custom). */
    var selectedPreset by mutableStateOf<SizePreset?>(SizePreset.M)
        private set

    /** Called when the tailor types in a measurement box. */
    fun editMeasurement(f: MeasurementField, text: String) {
        inputs[f] = text
        selectedPreset = null
    }

    fun applyPreset(p: SizePreset) {
        val keepNeck = currentMeasurements()
        var m = p.measurements()
        // Neck depths and sleeve length are style choices, keep what the tailor typed.
        for (f in listOf(MeasurementField.FRONT_NECK_DEPTH, MeasurementField.BACK_NECK_DEPTH, MeasurementField.SLEEVE_LENGTH)) {
            if (keepNeck.has(f)) m = m.with(f, keepNeck[f])
        }
        fillFrom(m)
        selectedPreset = p
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
        selectedPreset = SizePreset.M
    }

    fun selectCustomer(c: Customer) {
        customerId = c.id
        customerName = c.name
        customerPhone = c.phone
        fillFrom(Measurements.defaults().let { d -> Measurements(d.asMap() + c.measurements.asMap()) })
        selectedPreset = null // the customer's own measurements
    }

    /** Saves the current measurements under the current customer name. Returns an error or null. */
    fun saveCustomer(): String? {
        if (customerName.isBlank()) return tr("err.customer_name")
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

    fun changeAllowTurning(v: Boolean) {
        allowTurning = v
        settings.allowTurning = v
    }

    private var lastDraftKey: Any? = null
    private var lastDraft: DraftResult? = null
    private var lastCountedKey: Any? = null

    /**
     * Drafts and nests off the main thread. The result is cached, so the pattern screen and
     * the projector show exactly the same layout without computing it twice.
     */
    suspend fun draftAsync(model: GarmentModel): DraftResult {
        val key = listOf(model.id, currentMeasurements(), fabricWidthCm, foldedCloth, showAllowance, allowTurning, language, customerName.trim())
        lastDraft?.let { if (key == lastDraftKey) return it }
        val result = withContext(Dispatchers.Default) { draft(model) }
        // Always hand the result back on the main thread: it ends up in views and Compose state.
        return withContext(Dispatchers.Main.immediate) {
            lastDraftKey = key
            lastDraft = result
            val user = currentUser
            // Count a pattern once per design + measurements, not for every cloth option change.
            val countKey = listOf(model.id, key[1], key.last())
            if (result is DraftResult.Ok && user != null && countKey != lastCountedKey) {
                lastCountedKey = countKey
                withContext(Dispatchers.IO) { accounts.recordPattern(user.id) }
            }
            result
        }
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
                    allowTurning = allowTurning,
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
