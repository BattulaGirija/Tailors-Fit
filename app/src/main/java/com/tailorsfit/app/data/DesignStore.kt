package com.tailorsfit.app.data

import com.tailorsfit.pattern.blouse.BackDetail
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
    fun load(): State = if (file.exists()) decode(file.readText()) else State(emptyList(), emptySet())

    /** Reads a saved state (from the file or the server); an unreadable one counts as empty. */
    fun decode(json: String): State {
        return try {
            val o = JSONObject(json)
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

    fun encode(state: State): String {
        val o = JSONObject()
        o.put("custom", JSONArray().apply { state.custom.forEach { put(toJson(it)) } })
        o.put("hidden", JSONArray().apply { state.hidden.forEach { put(it) } })
        return o.toString()
    }

    @Synchronized
    fun save(state: State) {
        file.writeText(encode(state))
        apply(state)
    }

    fun apply(state: State = load()) = Catalog.configure(state.custom, state.hidden)

    private fun neckJson(n: NeckSpec) = JSONObject().put("shape", n.shape.name).put("widen", n.widen).put("depth", n.depthFactor)
    private fun neck(o: JSONObject) = NeckSpec(NeckShape.valueOf(o.getString("shape")), o.optDouble("widen", 0.0), o.optDouble("depth", 1.0))

    private fun toJson(m: BlouseModel) = JSONObject().apply {
        put("id", m.id); put("name", m.baseName); put("description", m.baseDescription)
        put("front", neckJson(m.front)); put("back", neckJson(m.back))
        put("sleeve", m.sleeve.name); put("opening", m.opening.name); put("princess", m.princess); put("body", m.body.name)
        put("backDetail", m.backDetail.name); put("collar", m.collar)
    }

    private fun fromJson(o: JSONObject) = BlouseModel(
        id = o.getString("id"),
        baseName = o.getString("name"),
        baseDescription = o.optString("description"),
        front = neck(o.getJSONObject("front")),
        back = neck(o.getJSONObject("back")),
        sleeve = SleeveStyle.valueOf(o.getString("sleeve")),
        opening = Opening.valueOf(o.getString("opening")),
        princess = o.optBoolean("princess"),
        body = runCatching { com.tailorsfit.pattern.blouse.BodyStyle.valueOf(o.optString("body")) }
            .getOrDefault(if (o.optBoolean("princess")) com.tailorsfit.pattern.blouse.BodyStyle.PRINCESS else com.tailorsfit.pattern.blouse.BodyStyle.THREE_DART),
        backDetail = runCatching { BackDetail.valueOf(o.optString("backDetail")) }.getOrDefault(BackDetail.NONE),
        collar = o.optBoolean("collar"),
    )
}
