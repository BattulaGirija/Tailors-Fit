package com.tailorsfit.app.data

import com.tailorsfit.pattern.i18n.tr
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class Role { TAILOR, ADMIN }

data class Account(
    val id: String,
    val name: String,
    val shopName: String,
    /** E-mail used to log in (stored lower-case, trimmed). */
    val login: String,
    val role: Role,
    val createdAt: Long,
    val phone: String = "",
    val patternsGenerated: Int = 0,
    val customerCount: Int = 0,
    val lastActiveAt: Long = createdAt,
    internal val salt: String = "",
    internal val hash: String = "",
)

sealed interface AuthResult {
    data class Success(val account: Account) : AuthResult
    data class Failure(val message: String) : AuthResult
}

/** Tailors log in with a 4-digit PIN. */
object Pin {
    fun problem(pin: String): String? = if (pin.length == 4 && pin.all { it in '0'..'9' }) null else tr("err.pin")

    /**
     * Firebase needs passwords of 6+ characters, so the PIN is stretched to one. The PIN reset
     * page (docs/reset.html) must stretch it exactly the same way.
     */
    fun toPassword(pin: String) = "TailorsFit-pin-$pin"
}

object Logins {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalise(email: String) = email.trim().lowercase().replace(" ", "")

    fun isValidEmail(email: String) = EMAIL.matches(email)

    /** Optional phone: empty, or 10–13 digits (spaces, dashes and a leading + are ignored). */
    fun normalisePhone(phone: String): String? {
        val p = phone.trim()
        if (p.isEmpty()) return ""
        val digits = p.removePrefix("+").filter { it.isDigit() }
        val clean = p.removePrefix("+").none { !it.isDigit() && it != ' ' && it != '-' }
        return if (clean && digits.length in 10..13) (if (p.startsWith("+")) "+" else "") + digits else null
    }

    /** Checks common to every backend; returns an error message or null. */
    fun signUpProblem(name: String, email: String, phone: String, pin: String): String? = when {
        name.isBlank() -> tr("err.name")
        !isValidEmail(normalise(email)) -> tr("err.email_invalid")
        normalisePhone(phone) == null -> tr("err.phone_invalid")
        else -> Pin.problem(pin)
    }
}

/**
 * Where accounts, customers and admin designs live.
 *
 * - [FirebaseBackend]: online, shared by every phone (used when the app is built with
 *   `app/google-services.json`).
 * - [LocalBackend]: on this phone only (builds without Firebase, and tests).
 */
interface Backend {
    /** True when data is online and shared between phones. */
    val online: Boolean

    /** The tailor still logged in from last time, or null. */
    fun restore(): Account?

    suspend fun signUp(name: String, shopName: String, phone: String, email: String, pin: String): AuthResult
    suspend fun logIn(email: String, pin: String): AuthResult
    fun logOut()

    /** E-mails a link for choosing a new PIN. Returns an error message, or null when sent. */
    suspend fun sendPinReset(email: String): String?

    /** True when the admin password still has to be chosen (on-phone accounts only). */
    fun adminNeedsSetup(): Boolean
    suspend fun adminLogIn(email: String, password: String): AuthResult
    fun adminLogOut()

    suspend fun tailors(): List<Account>
    suspend fun find(id: String): Account?
    suspend fun tailorCustomers(id: String): List<Customer>
    suspend fun deleteTailor(id: String)

    /** On this phone: sets [newPin]. Online: e-mails the tailor a reset link ([newPin] unused). */
    suspend fun adminResetPin(id: String, newPin: String): String?

    suspend fun recordPattern(accountId: String)

    // Keeping customers and designs in step with the server (no-ops on this phone only).
    suspend fun pullCustomers(userId: String): List<Customer>?
    fun pushCustomer(userId: String, customer: Customer, total: Int)
    fun removeCustomer(userId: String, customerId: String, total: Int)
    suspend fun pullDesigns(): String?
    fun pushDesigns(json: String)
}

object Passwords {
    private const val ITERATIONS = 12_000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.hexToBytes(), ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded.toHex()
    }

    /** Constant-time comparison so response time does not leak how much matched. */
    fun matches(password: String, salt: String, expected: String): Boolean {
        val actual = hash(password, salt)
        if (actual.length != expected.length) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].code xor expected[i].code)
        return diff == 0
    }

    fun problem(password: String): String? = if (password.isBlank() || password.length < 6) tr("err.password_short") else null

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** Accounts kept in a JSON file on this phone. */
class LocalBackend(private val context: Context) : Backend {
    private val file = File(context.filesDir, "accounts.json")
    private val settings = Settings(context)

    override val online = false

    @Synchronized
    private fun load(): MutableList<Account> {
        if (!file.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            MutableList(arr.length()) { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    private fun save(all: List<Account>) {
        val arr = JSONArray()
        all.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    private fun withCount(a: Account) = a.copy(customerCount = CustomerRepository(context, a.id).loadAll().size)

    override fun restore(): Account? = settings.sessionUserId?.let { id -> load().firstOrNull { it.id == id && it.role == Role.TAILOR } }

    override suspend fun signUp(name: String, shopName: String, phone: String, email: String, pin: String): AuthResult {
        synchronized(this) {
            Logins.signUpProblem(name, email, phone, pin)?.let { return AuthResult.Failure(it) }
            val id = Logins.normalise(email)
            val all = load()
            if (all.any { it.login == id }) return AuthResult.Failure(tr("err.login_taken"))
            val salt = Passwords.newSalt()
            val account = Account(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                shopName = shopName.trim(),
                login = id,
                role = Role.TAILOR,
                createdAt = System.currentTimeMillis(),
                phone = Logins.normalisePhone(phone) ?: "",
                salt = salt,
                hash = Passwords.hash(pin, salt),
            )
            save(all + account)
            settings.sessionUserId = account.id
            return AuthResult.Success(account)
        }
    }

    override suspend fun logIn(email: String, pin: String): AuthResult {
        synchronized(this) {
            val id = Logins.normalise(email)
            val all = load()
            val acc = all.firstOrNull { it.login == id && it.role == Role.TAILOR }
            if (acc == null || !Passwords.matches(pin, acc.salt, acc.hash)) return AuthResult.Failure(tr("err.login_wrong"))
            val updated = acc.copy(lastActiveAt = System.currentTimeMillis())
            save(all.map { if (it.id == acc.id) updated else it })
            settings.sessionUserId = updated.id
            return AuthResult.Success(updated)
        }
    }

    override fun logOut() {
        settings.sessionUserId = null
    }

    /** No e-mail without the online service: the admin sets a new PIN instead. */
    override suspend fun sendPinReset(email: String): String? = tr("forgot.offline")

    override fun adminNeedsSetup() = load().none { it.role == Role.ADMIN }

    /** The first admin log in on this phone sets the admin password. */
    override suspend fun adminLogIn(email: String, password: String): AuthResult {
        synchronized(this) {
            val all = load()
            val admin = all.firstOrNull { it.role == Role.ADMIN }
            if (admin == null) {
                Passwords.problem(password)?.let { return AuthResult.Failure(it) }
                val salt = Passwords.newSalt()
                val created = Account(
                    id = "admin", name = "Administrator", shopName = "", login = "admin", role = Role.ADMIN,
                    createdAt = System.currentTimeMillis(), salt = salt, hash = Passwords.hash(password, salt),
                )
                save(all + created)
                return AuthResult.Success(created)
            }
            return if (Passwords.matches(password, admin.salt, admin.hash)) AuthResult.Success(admin)
            else AuthResult.Failure(tr("err.admin_wrong"))
        }
    }

    override fun adminLogOut() = Unit

    override suspend fun tailors() = load().filter { it.role == Role.TAILOR }.map(::withCount).sortedByDescending { it.lastActiveAt }

    override suspend fun find(id: String) = load().firstOrNull { it.id == id }?.let(::withCount)

    override suspend fun tailorCustomers(id: String) = CustomerRepository(context, id).loadAll()

    override suspend fun deleteTailor(id: String) {
        synchronized(this) {
            save(load().filter { it.id != id || it.role == Role.ADMIN })
        }
    }

    override suspend fun adminResetPin(id: String, newPin: String): String? {
        synchronized(this) {
            Pin.problem(newPin)?.let { return it }
            val all = load()
            val acc = all.firstOrNull { it.id == id && it.role == Role.TAILOR } ?: return tr("admin.gone")
            val salt = Passwords.newSalt()
            save(all.map { if (it.id == id) acc.copy(salt = salt, hash = Passwords.hash(newPin, salt)) else it })
            return null
        }
    }

    override suspend fun recordPattern(accountId: String) {
        synchronized(this) {
            val all = load()
            save(all.map { if (it.id == accountId) it.copy(patternsGenerated = it.patternsGenerated + 1, lastActiveAt = System.currentTimeMillis()) else it })
        }
    }

    override suspend fun pullCustomers(userId: String): List<Customer>? = null
    override fun pushCustomer(userId: String, customer: Customer, total: Int) = Unit
    override fun removeCustomer(userId: String, customerId: String, total: Int) = Unit
    override suspend fun pullDesigns(): String? = null
    override fun pushDesigns(json: String) = Unit

    private fun toJson(a: Account) = JSONObject().apply {
        put("id", a.id); put("name", a.name); put("shop", a.shopName); put("login", a.login); put("phone", a.phone)
        put("role", a.role.name); put("createdAt", a.createdAt); put("patterns", a.patternsGenerated)
        put("lastActiveAt", a.lastActiveAt); put("salt", a.salt); put("hash", a.hash)
    }

    private fun fromJson(o: JSONObject) = Account(
        id = o.getString("id"),
        name = o.optString("name"),
        shopName = o.optString("shop"),
        login = o.getString("login"),
        role = runCatching { Role.valueOf(o.optString("role")) }.getOrDefault(Role.TAILOR),
        createdAt = o.optLong("createdAt"),
        phone = o.optString("phone"),
        patternsGenerated = o.optInt("patterns"),
        lastActiveAt = o.optLong("lastActiveAt"),
        salt = o.optString("salt"),
        hash = o.optString("hash"),
    )
}
