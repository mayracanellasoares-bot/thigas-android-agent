package br.com.thigas.agent

import org.json.JSONArray
import org.json.JSONObject

object PortalPlanCodec {
    fun toJson(plan: PortalPlan): String = JSONObject().apply {
        put("date", plan.date)
        put("className", plan.className)
        put("subject", plan.subject)
        put("content", plan.content)
        put("periodSlots", JSONArray(plan.periodSlots))
        put("absentees", JSONArray(plan.absentees))
    }.toString()

    fun fromJson(raw: String?): PortalPlan? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            val slots = mutableListOf<String>()
            val absentees = mutableListOf<String>()
            o.optJSONArray("periodSlots")?.let { a ->
                for (i in 0 until a.length()) slots += a.optString(i)
            }
            o.optJSONArray("absentees")?.let { a ->
                for (i in 0 until a.length()) absentees += a.optString(i)
            }
            PortalPlan(
                date = o.optString("date"),
                className = o.optString("className"),
                subject = o.optString("subject"),
                content = o.optString("content"),
                periodSlots = slots,
                absentees = absentees
            )
        }.getOrNull()
    }
}
