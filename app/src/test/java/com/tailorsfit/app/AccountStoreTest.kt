package com.tailorsfit.app

import androidx.test.core.app.ApplicationProvider
import com.tailorsfit.app.data.AuthResult
import com.tailorsfit.app.data.CustomerRepository
import com.tailorsfit.app.data.Customer
import com.tailorsfit.app.data.LocalAccountStore
import com.tailorsfit.app.data.Role
import com.tailorsfit.pattern.model.Measurements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val store = LocalAccountStore(context)

    @Test
    fun signUpThenLogIn() {
        val r = store.signUp("Ravi", "Ravi Tailors", "98765 43210", "secret1")
        assertTrue(r is AuthResult.Success)
        assertTrue(store.logIn("9876543210", "secret1") is AuthResult.Success)
        assertTrue(store.logIn("9876543210", "wrong-pass") is AuthResult.Failure)
        assertTrue(store.logIn("9999999999", "secret1") is AuthResult.Failure)
    }

    @Test
    fun rejectsDuplicatesWeakPasswordsAndBadLogins() {
        assertTrue(store.signUp("A", "", "a@shop.in", "secret1") is AuthResult.Success)
        assertTrue(store.signUp("B", "", "A@Shop.in ", "secret2") is AuthResult.Failure)
        assertTrue(store.signUp("C", "", "c@shop.in", "123") is AuthResult.Failure)
        assertTrue(store.signUp("D", "", "not-a-login", "secret1") is AuthResult.Failure)
        assertTrue(store.signUp("", "", "d@shop.in", "secret1") is AuthResult.Failure)
    }

    @Test
    fun passwordsAreNotStoredInPlainText() {
        store.signUp("Ravi", "", "ravi@shop.in", "my-secret-pw")
        val raw = File(context.filesDir, "accounts.json").readText()
        assertFalse(raw.contains("my-secret-pw"))
    }

    @Test
    fun adminIsSetUpOnceAndIsSeparateFromTailors() {
        assertFalse(store.adminExists())
        assertTrue(store.createAdmin("admin-pass") is AuthResult.Success)
        assertTrue(store.adminExists())
        assertTrue(store.createAdmin("other-pass") is AuthResult.Failure)
        assertTrue(store.adminLogIn("admin-pass") is AuthResult.Success)
        assertTrue(store.adminLogIn("nope-nope") is AuthResult.Failure)
        // The admin cannot log in through the tailor form, and is not listed as a tailor.
        assertTrue(store.logIn("admin", "admin-pass") is AuthResult.Failure)
        store.signUp("Ravi", "", "ravi@shop.in", "secret1")
        assertEquals(listOf(Role.TAILOR), store.tailors().map { it.role })
    }

    @Test
    fun eachTailorHasTheirOwnCustomers() {
        val a = (store.signUp("A", "", "a@shop.in", "secret1") as AuthResult.Success).account
        val b = (store.signUp("B", "", "b@shop.in", "secret1") as AuthResult.Success).account
        CustomerRepository(context, a.id).save(Customer(name = "Lakshmi", measurements = Measurements.defaults()))
        assertEquals(1, CustomerRepository(context, a.id).loadAll().size)
        assertEquals(0, CustomerRepository(context, b.id).loadAll().size)
    }

    @Test
    fun forgottenPasswordIsResetWithTheSecurityAnswer() {
        store.signUp("Ravi", "", "ravi@shop.in", "old-secret", "sq.city", "Hyderabad")
        assertEquals("sq.city", store.securityQuestion(" Ravi@Shop.in"))
        assertTrue(store.resetPassword("ravi@shop.in", "Vizag", "new-secret") is AuthResult.Failure)
        assertTrue(store.logIn("ravi@shop.in", "old-secret") is AuthResult.Success)
        // Case and extra spaces in the answer do not matter.
        assertTrue(store.resetPassword("ravi@shop.in", "  hyderabad ", "new-secret") is AuthResult.Success)
        assertTrue(store.logIn("ravi@shop.in", "old-secret") is AuthResult.Failure)
        assertTrue(store.logIn("ravi@shop.in", "new-secret") is AuthResult.Success)
        // The answer is hashed, never stored as typed.
        assertFalse(File(context.filesDir, "accounts.json").readText().lowercase().contains("hyderabad"))
    }

    @Test
    fun wrongAnswersAreLimited() {
        store.signUp("Ravi", "", "ravi@shop.in", "old-secret", "sq.pet", "Moti")
        repeat(5) { assertTrue(store.resetPassword("ravi@shop.in", "guess$it", "new-secret") is AuthResult.Failure) }
        assertTrue(store.resetPassword("ravi@shop.in", "Moti", "new-secret") is AuthResult.Failure)
        assertTrue(store.logIn("ravi@shop.in", "old-secret") is AuthResult.Success)
    }

    @Test
    fun accountsWithoutAQuestionAreResetByTheAdmin() {
        val a = (store.signUp("A", "", "a@shop.in", "secret1") as AuthResult.Success).account
        assertEquals(null, store.securityQuestion("a@shop.in"))
        assertEquals(null, store.securityQuestion("nobody@shop.in"))
        assertTrue(store.resetPassword("a@shop.in", "anything", "secret2") is AuthResult.Failure)
        assertTrue(store.adminResetPassword(a.id, "123") is AuthResult.Failure)
        assertTrue(store.adminResetPassword(a.id, "secret2") is AuthResult.Success)
        assertTrue(store.logIn("a@shop.in", "secret2") is AuthResult.Success)
    }

    @Test
    fun signUpNeedsAnAnswerWhenAQuestionIsChosen() {
        assertTrue(store.signUp("A", "", "a@shop.in", "secret1", "sq.pet", " ") is AuthResult.Failure)
        assertTrue(store.signUp("A", "", "a@shop.in", "secret1", "sq.unknown", "Moti") is AuthResult.Failure)
    }

    @Test
    fun patternsAreCounted() {
        val a = (store.signUp("A", "", "a@shop.in", "secret1") as AuthResult.Success).account
        store.recordPattern(a.id)
        store.recordPattern(a.id)
        assertEquals(2, store.find(a.id)!!.patternsGenerated)
    }
}
