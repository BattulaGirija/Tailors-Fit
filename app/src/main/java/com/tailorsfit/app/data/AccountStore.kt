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
    /** Phone number or e-mail used to log in (stored lower-case, trimmed). */
    val login: String,
    val role: Role,
    val createdAt: Long,
    val patternsGenerated: Int = 0,
    val lastActiveAt: Long = createdAt,
    internal val salt: String = "",
    internal val hash: String = "",
)

sealed interface AuthResult {
    data class Success(val account: Account) : AuthResult
    data class Failure(val message: String) : AuthResult
}

/**
 * Where accounts live. Today this is [LocalAccountStore] (on this phone only); an online
 * backend (e.g. Firebase Auth + Firestore) can implement the same interface so that an admin
 * sees tailors from every phone.
 */
interface AccountStore {
    fun signUp(name: String, shopName: String, login: String, password: String): AuthResult
    fun logIn(login: String, password: String): AuthResult
    fun adminExists(): Boolean
    fun createAdmin(password: String): AuthResult
    fun adminLogIn(password: String): AuthResult
    fun tailors(): List<Account>
    fun find(id: String): Account?
    fun recordPattern(accountId: String)
    fun deleteTailor(id: String)
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

    fun problem(password: String): String? = when {
        password.length < 6 -> tr("err.password_short")
        password.isBlank() -> tr("err.password_short")
        else -> null
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

class LocalAccountStore(context: Context) : AccountStore {
    private val file = File(context.filesDir, "accounts.json")

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

    private fun normalise(login: String) = login.trim().lowercase().replace(" ", "")

    @Synchronized
    override fun signUp(name: String, shopName: String, login: String, password: String): AuthResult {
        val id = normalise(login)
        if (name.isBlank()) return AuthResult.Failure(tr("err.name"))
        if (!isValidLogin(id)) return AuthResult.Failure(tr("err.login_invalid"))
        Passwords.problem(password)?.let { return AuthResult.Failure(it) }
        val all = load()
        if (all.any { it.login == id }) return AuthResult.Failure(tr("err.login_taken"))
        val salt = Passwords.newSalt()
        val now = System.currentTimeMillis()
        val account = Account(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            shopName = shopName.trim(),
            login = id,
            role = Role.TAILOR,
            createdAt = now,
            salt = salt,
            hash = Passwords.hash(password, salt),
        )
        save(all + account)
        return AuthResult.Success(account)
    }

    @Synchronized
    override fun logIn(login: String, password: String): AuthResult {
        val id = normalise(login)
        val all = load()
        val acc = all.firstOrNull { it.login == id && it.role == Role.TAILOR }
        if (acc == null || !Passwords.matches(password, acc.salt, acc.hash)) {
            return AuthResult.Failure(tr("err.login_wrong"))
        }
        val updated = acc.copy(lastActiveAt = System.currentTimeMillis())
        save(all.map { if (it.id == acc.id) updated else it })
        return AuthResult.Success(updated)
    }

    override fun adminExists() = load().any { it.role == Role.ADMIN }

    @Synchronized
    override fun createAdmin(password: String): AuthResult {
        val all = load()
        if (all.any { it.role == Role.ADMIN }) return AuthResult.Failure(tr("err.admin_exists"))
        Passwords.problem(password)?.let { return AuthResult.Failure(it) }
        val salt = Passwords.newSalt()
        val admin = Account(
            id = "admin", name = "Administrator", shopName = "", login = "admin", role = Role.ADMIN,
            createdAt = System.currentTimeMillis(), salt = salt, hash = Passwords.hash(password, salt),
        )
        save(all + admin)
        return AuthResult.Success(admin)
    }

    override fun adminLogIn(password: String): AuthResult {
        val admin = load().firstOrNull { it.role == Role.ADMIN } ?: return AuthResult.Failure(tr("err.admin_missing"))
        return if (Passwords.matches(password, admin.salt, admin.hash)) AuthResult.Success(admin)
        else AuthResult.Failure(tr("err.admin_wrong"))
    }

    override fun tailors() = load().filter { it.role == Role.TAILOR }.sortedByDescending { it.lastActiveAt }

    override fun find(id: String) = load().firstOrNull { it.id == id }

    @Synchronized
    override fun recordPattern(accountId: String) {
        val all = load()
        save(all.map { if (it.id == accountId) it.copy(patternsGenerated = it.patternsGenerated + 1, lastActiveAt = System.currentTimeMillis()) else it })
    }

    @Synchronized
    override fun deleteTailor(id: String) {
        save(load().filter { it.id != id || it.role == Role.ADMIN })
    }

    private fun toJson(a: Account) = JSONObject().apply {
        put("id", a.id); put("name", a.name); put("shop", a.shopName); put("login", a.login)
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
        patternsGenerated = o.optInt("patterns"),
        lastActiveAt = o.optLong("lastActiveAt"),
        salt = o.optString("salt"),
        hash = o.optString("hash"),
    )

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        fun isValidLogin(login: String): Boolean {
            val digits = login.removePrefix("+").filter { it.isDigit() }
            return EMAIL.matches(login) || (digits.length in 10..13 && digits.length == login.removePrefix("+").length)
        }
    }
}
