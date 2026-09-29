package com.tailorsfit.app

import androidx.test.core.app.ApplicationProvider
import com.tailorsfit.app.data.AuthResult
import com.tailorsfit.app.data.Customer
import com.tailorsfit.app.data.CustomerRepository
import com.tailorsfit.app.data.DesignStore
import com.tailorsfit.app.data.LocalBackend
import com.tailorsfit.app.data.Logins
import com.tailorsfit.app.data.Pin
import com.tailorsfit.app.data.Role
import com.tailorsfit.pattern.model.Measurements
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalBackendTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val store = LocalBackend(context)

    @Test
    fun signUpThenLogInWithPin() = runBlocking {
        val r = store.signUp("Ravi", "Ravi Tailors", "98765 43210", "Ravi@Shop.in ", "1234")
        assertTrue(r is AuthResult.Success)
        assertEquals("9876543210", (r as AuthResult.Success).account.phone)
        assertTrue(store.logIn("ravi@shop.in", "1234") is AuthResult.Success)
        assertTrue(store.logIn("ravi@shop.in", "4321") is AuthResult.Failure)
        assertTrue(store.logIn("other@shop.in", "1234") is AuthResult.Failure)
    }

    @Test
    fun rejectsDuplicatesBadPinsAndBadDetails() = runBlocking {
        assertTrue(store.signUp("A", "", "", "a@shop.in", "1111") is AuthResult.Success)
        assertTrue(store.signUp("B", "", "", "A@Shop.in", "2222") is AuthResult.Failure)
        assertTrue(store.signUp("C", "", "", "c@shop.in", "123") is AuthResult.Failure)
        assertTrue(store.signUp("C", "", "", "c@shop.in", "12a4") is AuthResult.Failure)
        assertTrue(store.signUp("C", "", "", "c@shop.in", "12345") is AuthResult.Failure)
        assertTrue(store.signUp("D", "", "", "not-an-email", "1111") is AuthResult.Failure)
        assertTrue(store.signUp("E", "", "12345", "e@shop.in", "1111") is AuthResult.Failure)
        assertTrue(store.signUp("", "", "", "f@shop.in", "1111") is AuthResult.Failure)
    }

    @Test
    fun pinsAreNotStoredInPlainText() = runBlocking {
        store.signUp("Ravi", "", "", "ravi@shop.in", "8642")
        val raw = File(context.filesDir, "accounts.json").readText()
        assertFalse(raw.contains("8642"))
    }

    @Test
    fun sessionIsRememberedUntilLogOut() = runBlocking {
        val a = (store.signUp("Ravi", "", "", "ravi@shop.in", "1234") as AuthResult.Success).account
        assertEquals(a.id, LocalBackend(context).restore()?.id)
        store.logOut()
        assertNull(LocalBackend(context).restore())
    }

    @Test
    fun adminIsSetUpOnceAndIsSeparateFromTailors() = runBlocking {
        assertTrue(store.adminNeedsSetup())
        assertTrue(store.adminLogIn("", "admin-pass") is AuthResult.Success)
        assertFalse(store.adminNeedsSetup())
        assertTrue(store.adminLogIn("", "admin-pass") is AuthResult.Success)
        assertTrue(store.adminLogIn("", "nope-nope") is AuthResult.Failure)
        store.signUp("Ravi", "", "", "ravi@shop.in", "1234")
        assertEquals(listOf(Role.TAILOR), store.tailors().map { it.role })
    }

    @Test
    fun adminResetsAPinOnThisPhone() = runBlocking {
        val a = (store.signUp("A", "", "", "a@shop.in", "1111") as AuthResult.Success).account
        // No e-mail without the online service.
        assertNotNull(store.sendPinReset("a@shop.in"))
        assertNotNull(store.adminResetPin(a.id, "12"))
        assertNull(store.adminResetPin(a.id, "2222"))
        assertTrue(store.logIn("a@shop.in", "1111") is AuthResult.Failure)
        assertTrue(store.logIn("a@shop.in", "2222") is AuthResult.Success)
    }

    @Test
    fun eachTailorHasTheirOwnCustomers() = runBlocking {
        val a = (store.signUp("A", "", "", "a@shop.in", "1111") as AuthResult.Success).account
        val b = (store.signUp("B", "", "", "b@shop.in", "1111") as AuthResult.Success).account
        CustomerRepository(context, a.id).save(Customer(name = "Lakshmi", measurements = Measurements.defaults()))
        assertEquals(1, store.tailorCustomers(a.id).size)
        assertEquals(0, store.tailorCustomers(b.id).size)
        assertEquals(1, store.find(a.id)!!.customerCount)
    }

    @Test
    fun patternsAreCounted() = runBlocking {
        val a = (store.signUp("A", "", "", "a@shop.in", "1111") as AuthResult.Success).account
        store.recordPattern(a.id)
        store.recordPattern(a.id)
        assertEquals(2, store.find(a.id)!!.patternsGenerated)
    }

    @Test
    fun customersAndDesignsSurviveTheTripThroughJson() {
        val c = Customer(name = "Lakshmi", phone = "9876543210", measurements = Measurements.defaults())
        val back = CustomerRepository.fromJson(CustomerRepository.toJson(c))
        assertEquals(c.copy(measurements = back.measurements), back)
        assertEquals(c.measurements.asMap(), back.measurements.asMap())
        val designs = DesignStore(context)
        val state = DesignStore.State(emptyList(), setOf("blouse_boat"))
        assertEquals(state, designs.decode(designs.encode(state)))
    }

    @Test
    fun pinIsStretchedTheSameWayAsOnTheResetPage() {
        // docs/reset.html builds the same password; change both together.
        assertEquals("TailorsFit-pin-0427", Pin.toPassword("0427"))
        assertTrue(File("../docs/reset.html").readText().contains("\"TailorsFit-pin-\" + pin"))
        assertEquals("", Logins.normalisePhone(" "))
        assertEquals("+919876543210", Logins.normalisePhone("+91 98765-43210"))
    }
}
