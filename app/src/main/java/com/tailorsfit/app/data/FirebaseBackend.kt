package com.tailorsfit.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tailorsfit.pattern.i18n.I18n
import com.tailorsfit.pattern.i18n.tr
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Picks the backend: Firebase when the app was built with its config, else this phone only. */
object Backends {
    /** Tests set this so they never talk to the server. */
    @Volatile
    var forceLocal = false

    fun create(context: Context): Backend {
        if (forceLocal) return LocalBackend(context)
        val app = runCatching { FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context) }.getOrNull()
        return if (app != null) FirebaseBackend(context) else LocalBackend(context)
    }
}

/**
 * Accounts in Firebase Authentication (e-mail + PIN), data in Cloud Firestore:
 *
 * - `users/{uid}`: name, shop, phone, email, createdAt, lastActiveAt, patterns, customers, disabled
 * - `users/{uid}/customers/{id}`: one customer (`json`, `name`, `updatedAt`)
 * - `config/designs`: the admin's designs and hidden designs (`json`)
 * - `admins/{uid}`: present for admin accounts (created by hand in the Firebase console)
 *
 * Access rules are in `firestore.rules`. Firestore keeps a copy on the phone, so saving works
 * offline and is sent when the phone is back online.
 */
class FirebaseBackend(context: Context) : Backend {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /** The logged-in tailor's profile, so the app opens straight away even when offline. */
    private val cache = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    override val online = true

    private fun users() = db.collection("users")

    override fun restore(): Account? {
        val user = auth.currentUser ?: return null
        val json = cache.getString("account_${user.uid}", null) ?: return null
        return runCatching { fromCache(JSONObject(json)) }.getOrNull()
    }

    private fun remember(a: Account) {
        cache.edit().clear().putString("account_${a.id}", toCache(a).toString()).apply()
    }

    override suspend fun signUp(name: String, shopName: String, phone: String, email: String, pin: String): AuthResult = guard(tr("err.email_invalid")) {
        Logins.signUpProblem(name, email, phone, pin)?.let { return@guard AuthResult.Failure(it) }
        val login = Logins.normalise(email)
        val uid = auth.createUserWithEmailAndPassword(login, Pin.toPassword(pin)).await().user!!.uid
        val now = System.currentTimeMillis()
        val account = Account(
            id = uid, name = name.trim(), shopName = shopName.trim(), login = login, role = Role.TAILOR,
            createdAt = now, phone = Logins.normalisePhone(phone) ?: "",
        )
        users().document(uid).set(profile(account)).await()
        remember(account)
        AuthResult.Success(account)
    }

    override suspend fun logIn(email: String, pin: String): AuthResult = guard(tr("err.login_wrong")) {
        val login = Logins.normalise(email)
        if (!Logins.isValidEmail(login)) return@guard AuthResult.Failure(tr("err.email_invalid"))
        if (Pin.problem(pin) != null) return@guard AuthResult.Failure(tr("err.login_wrong"))
        val uid = auth.signInWithEmailAndPassword(login, Pin.toPassword(pin)).await().user!!.uid
        val doc = users().document(uid).get().await()
        if (doc.getBoolean("disabled") == true) {
            auth.signOut()
            return@guard AuthResult.Failure(tr("err.account_disabled"))
        }
        val now = System.currentTimeMillis()
        val account = if (doc.exists()) fromDoc(doc).copy(lastActiveAt = now) else {
            // Signed up but the profile was never saved (e.g. the network dropped): make one now.
            Account(uid, login.substringBefore('@'), "", login, Role.TAILOR, now).also { users().document(uid).set(profile(it)) }
        }
        users().document(uid).update("lastActiveAt", now)
        remember(account)
        AuthResult.Success(account)
    }

    override fun logOut() {
        cache.edit().clear().apply()
        auth.signOut()
    }

    override suspend fun sendPinReset(email: String): String? {
        val login = Logins.normalise(email)
        if (!Logins.isValidEmail(login)) return tr("err.email_invalid")
        val r = guard(tr("err.email_invalid")) {
            auth.setLanguageCode(I18n.language.code)
            auth.sendPasswordResetEmail(login).await()
            AuthResult.Success(Account("", "", "", login, Role.TAILOR, 0))
        }
        return (r as? AuthResult.Failure)?.message
    }

    override fun adminNeedsSetup() = false

    override suspend fun adminLogIn(email: String, password: String): AuthResult = guard(tr("err.admin_wrong")) {
        val login = Logins.normalise(email)
        if (!Logins.isValidEmail(login) || password.isEmpty()) return@guard AuthResult.Failure(tr("err.admin_wrong"))
        val uid = auth.signInWithEmailAndPassword(login, password).await().user!!.uid
        if (!db.collection("admins").document(uid).get().await().exists()) {
            auth.signOut()
            return@guard AuthResult.Failure(tr("err.not_admin"))
        }
        AuthResult.Success(Account(uid, "Administrator", "", login, Role.ADMIN, 0))
    }

    override fun adminLogOut() = auth.signOut()

    override suspend fun tailors(): List<Account> =
        runCatching { users().get().await().documents.filter { it.getBoolean("disabled") != true }.map(::fromDoc) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.lastActiveAt }

    override suspend fun find(id: String): Account? =
        runCatching { users().document(id).get().await().takeIf { it.exists() && it.getBoolean("disabled") != true }?.let(::fromDoc) }.getOrNull()

    override suspend fun tailorCustomers(id: String): List<Customer> = pullCustomers(id) ?: emptyList()

    /**
     * Stops the tailor from logging in and hides them from the list. Their login itself can only
     * be removed in the Firebase console (Authentication → Users).
     */
    override suspend fun deleteTailor(id: String) {
        runCatching { users().document(id).update("disabled", true).await() }
    }

    override suspend fun adminResetPin(id: String, newPin: String): String? {
        val email = find(id)?.login ?: return tr("admin.gone")
        return sendPinReset(email)
    }

    override suspend fun recordPattern(accountId: String) {
        users().document(accountId).update("patterns", FieldValue.increment(1), "lastActiveAt", System.currentTimeMillis())
    }

    override suspend fun pullCustomers(userId: String): List<Customer>? = runCatching {
        users().document(userId).collection("customers").get().await().documents.mapNotNull { d ->
            d.getString("json")?.let { runCatching { CustomerRepository.fromJson(JSONObject(it)) }.getOrNull() }
        }.sortedByDescending { it.updatedAt }
    }.getOrNull()

    override fun pushCustomer(userId: String, customer: Customer, total: Int) {
        users().document(userId).collection("customers").document(customer.id).set(
            mapOf("json" to CustomerRepository.toJson(customer).toString(), "name" to customer.name, "updatedAt" to customer.updatedAt),
        )
        users().document(userId).update("customers", total)
    }

    override fun removeCustomer(userId: String, customerId: String, total: Int) {
        users().document(userId).collection("customers").document(customerId).delete()
        users().document(userId).update("customers", total)
    }

    override suspend fun pullDesigns(): String? =
        runCatching { db.collection("config").document("designs").get().await().getString("json") }.getOrNull()

    override fun pushDesigns(json: String) {
        db.collection("config").document("designs").set(mapOf("json" to json), SetOptions.merge())
    }

    /** Turns Firebase errors into messages a tailor understands. */
    private inline fun guard(wrong: String, block: () -> AuthResult): AuthResult = try {
        block()
    } catch (e: FirebaseAuthUserCollisionException) {
        AuthResult.Failure(tr("err.login_taken"))
    } catch (e: FirebaseAuthInvalidUserException) {
        AuthResult.Failure(wrong)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        AuthResult.Failure(wrong)
    } catch (e: FirebaseTooManyRequestsException) {
        AuthResult.Failure(tr("err.too_many_tries"))
    } catch (e: FirebaseNetworkException) {
        AuthResult.Failure(tr("err.network"))
    } catch (e: Exception) {
        AuthResult.Failure(tr("err.server", e.message ?: e.javaClass.simpleName))
    }

    private fun profile(a: Account) = mapOf(
        "name" to a.name, "shop" to a.shopName, "phone" to a.phone, "email" to a.login,
        "createdAt" to a.createdAt, "lastActiveAt" to a.lastActiveAt, "patterns" to a.patternsGenerated, "customers" to a.customerCount,
    )

    private fun fromDoc(d: DocumentSnapshot) = Account(
        id = d.id,
        name = d.getString("name") ?: "",
        shopName = d.getString("shop") ?: "",
        login = d.getString("email") ?: "",
        role = Role.TAILOR,
        createdAt = d.getLong("createdAt") ?: 0,
        phone = d.getString("phone") ?: "",
        patternsGenerated = (d.getLong("patterns") ?: 0).toInt(),
        customerCount = (d.getLong("customers") ?: 0).toInt(),
        lastActiveAt = d.getLong("lastActiveAt") ?: 0,
    )

    private fun toCache(a: Account) = JSONObject().apply {
        put("id", a.id); put("name", a.name); put("shop", a.shopName); put("login", a.login)
        put("phone", a.phone); put("createdAt", a.createdAt)
    }

    private fun fromCache(o: JSONObject) = Account(
        id = o.getString("id"), name = o.optString("name"), shopName = o.optString("shop"), login = o.getString("login"),
        role = Role.TAILOR, createdAt = o.optLong("createdAt"), phone = o.optString("phone"),
    )
}
