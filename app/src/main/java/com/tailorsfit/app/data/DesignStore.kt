package com.tailorsfit.app.data

import android.content.Context
import com.tailorsfit.pattern.blouse.BlouseModel
import com.tailorsfit.pattern.blouse.NeckShape
import com.tailorsfit.pattern.blouse.NeckSpec
import com.tailorsfit.pattern.blouse.Opening
import com.tailorsfit.pattern.blouse.SleeveStyle
import com.tailorsfit.pattern.model.Catalog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Admin-made designs and hidden designs, applied to [Catalog] on start and on every change. */
class DesignStore(context: Context) {
    private val file = File(context.filesDir, "designs.json")

    data class State(val custom: List<BlouseModel>, val hidden: Set<String>)

    @Synchronized
    fun load(): State {
        if (!file.exists()) return State(emptyList(), emptySet())
        return try {
            val o = JSONObject(file.readText())
            val arr = o.optJSONArray("custom") ?: JSONArray()
            val hidden = o.optJSONArray("hidden") ?: JSONArray()
            State(
                (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() },
                (0 until hidden.length()).map { hidden.getString(it) }.toSet(),
            )
        } catch (e: Exception) {
            State(emptyList(), emptySet())
        }
    }

    @Synchronized
    fun save(state: State) {
        val o = JSONObject()
        o.put("custom", JSONArray().apply { state.custom.forEach { put(toJson(it)) } })
        o.put("hidden", JSONArray().apply { state.hidden.forEach { put(it) } })
        file.writeText(o.toString())
        apply(state)
    }

    fun apply(state: State = load()) = Catalog.configure(state.custom, state.hidden)

    private fun neckJson(n: NeckSpec) = JSONObject().put("shape", n.shape.name).put("widen", n.widen).put("depth", n.depthFactor)
    private fun neck(o: JSONObject) = NeckSpec(NeckShape.valueOf(o.getString("shape")), o.optDouble("widen", 0.0), o.optDouble("depth", 1.0))

    private fun toJson(m: BlouseModel) = JSONObject().apply {
        put("id", m.id); put("name", m.name); put("description", m.description)
        put("front", neckJson(m.front)); put("back", neckJson(m.back))
        put("sleeve", m.sleeve.name); put("opening", m.opening.name); put("princess", m.princess)
    }

    private fun fromJson(o: JSONObject) = BlouseModel(
        id = o.getString("id"),
        name = o.getString("name"),
        description = o.optString("description"),
        front = neck(o.getJSONObject("front")),
        back = neck(o.getJSONObject("back")),
        sleeve = SleeveStyle.valueOf(o.getString("sleeve")),
        opening = Opening.valueOf(o.getString("opening")),
        princess = o.optBoolean("princess"),
    )
}
