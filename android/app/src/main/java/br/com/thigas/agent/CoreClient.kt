package br.com.thigas.agent

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CoreClient(private val baseUrl: String = "http://127.0.0.1:8765") {
    fun today(): List<PortalPlan> {
        val conn = URL("$baseUrl/api/professor/today").openConnection() as HttpURLConnection
        conn.connectTimeout = 2500
        conn.readTimeout = 3500
        conn.requestMethod = "GET"
        conn.inputStream.bufferedReader().use { reader ->
            val root = JSONObject(reader.readText())
            val arr = root.getJSONArray("blocks")
            return (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                val slots = mutableListOf<String>()
                val a = b.optJSONArray("period_slots")
                if (a != null) for (j in 0 until a.length()) slots += a.getString(j)
                if (slots.isEmpty()) slots += "${b.getString("start")} - ${b.getString("end")}" 
                PortalPlan(
                    date = b.optString("date"),
                    className = b.optString("class_name"),
                    subject = b.optString("subject"),
                    content = b.optString("content"),
                    periodSlots = slots
                )
            }
        }
    }
}
