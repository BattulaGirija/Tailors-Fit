package com.tailorsfit.app.data

import android.content.Context
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.Measurements
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val notes: String = "",
    /** Measurements in centimetres. */
    val measurements: Measurements,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Keeps customers and their measurements in a small JSON file in app storage. */
class CustomerRepository(context: Context, ownerId: String? = null) {
    // Each tailor has their own customer book.
    private val file = File(context.filesDir, if (ownerId == null) "customers.json" else "customers_$ownerId.json")

    @Synchronized
    fun loadAll(): List<Customer> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(customer: Customer): List<Customer> {
        val all = loadAll().filter { it.id != customer.id } + customer.copy(updatedAt = System.currentTimeMillis())
        write(all)
        return all.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun delete(id: String): List<Customer> {
        val all = loadAll().filter { it.id != id }
        write(all)
        return all
    }

    private fun write(all: List<Customer>) {
        val arr = JSONArray()
        all.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    private fun toJson(c: Customer) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("phone", c.phone)
        put("notes", c.notes)
        put("updatedAt", c.updatedAt)
        put("measurements", JSONObject().apply {
            c.measurements.asMap().forEach { (f, v) -> if (!v.isNaN()) put(f.key, v) }
        })
    }

    private fun fromJson(o: JSONObject): Customer {
        val mo = o.optJSONObject("measurements") ?: JSONObject()
        val values = HashMap<MeasurementField, Double>()
        mo.keys().forEach { key ->
            MeasurementField.byKey(key)?.let { values[it] = mo.getDouble(key) }
        }
        return Customer(
            id = o.getString("id"),
            name = o.optString("name"),
            phone = o.optString("phone"),
            notes = o.optString("notes"),
            measurements = Measurements(values),
            updatedAt = o.optLong("updatedAt"),
        )
    }
}
