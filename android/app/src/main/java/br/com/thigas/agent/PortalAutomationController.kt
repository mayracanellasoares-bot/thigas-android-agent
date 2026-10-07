package br.com.thigas.agent

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class PortalAutomationController(
    private val webView: WebView,
    private val assetScript: String,
    private val onStatus: (String) -> Unit,
    private val onNeedUser: (String) -> Unit,
    private val onDone: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    @Volatile private var paused = false
    private var phase = "open_frequency"
    private var startedAt = 0L
    private lateinit var plan: PortalPlan

    fun stop() { running.set(false); paused = false }

    fun isPaused(): Boolean = paused

    fun resume() {
        if (!running.get()) return
        paused = false
        handler.postDelayed({ tick() }, 250)
    }

    fun map(callback: (String) -> Unit) {
        ensureInjected {
            webView.evaluateJavascript("JSON.stringify(window.ThigasPortal.scan())") { raw ->
                callback(decodeJsString(raw))
            }
        }
    }

    fun start(plan: PortalPlan) {
        if (running.getAndSet(true)) return
        paused = false
        this.plan = plan
        phase = "open_frequency"
        startedAt = System.currentTimeMillis()
        onStatus("Iniciando ação para ${plan.className}…")
        tick()
    }

    fun continueAfterManualStep() {
        if (!running.get()) return
        paused = false
        handler.postDelayed({ tick() }, 250)
    }

    private fun tick() {
        if (!running.get() || paused) return
        if (System.currentTimeMillis() - startedAt > 180_000) {
            running.set(false); onNeedUser("Tempo esgotado. Nenhum novo clique será feito."); return
        }
        ensureInjected {
            val js = "window.ThigasPortal.step(${planJson(plan)}, ${JSONObject.quote(phase)})"
            webView.evaluateJavascript("JSON.stringify($js)") { raw ->
                val decoded = decodeJsString(raw)
                val obj = runCatching { JSONObject(decoded) }.getOrNull()
                if (obj == null) {
                    paused = true; onNeedUser("Resposta inválida do portal. Use Mapear."); return@evaluateJavascript
                }
                val msg = obj.optString("message", phase)
                onStatus(msg)
                if (obj.optBoolean("done", false)) {
                    running.set(false); phase = "done"; onDone(msg); return@evaluateJavascript
                }
                if (obj.optBoolean("needsUser", false)) {
                    paused = true; onNeedUser(msg + detail(obj)); return@evaluateJavascript
                }
                val next = obj.optString("nextPhase")
                if (next.isNotBlank()) phase = next
                handler.postDelayed({ tick() }, if (obj.optBoolean("waiting", false)) 1200 else 800)
            }
        }
    }

    private fun ensureInjected(after: () -> Unit) {
        val host = runCatching { android.net.Uri.parse(webView.url ?: "").host ?: "" }.getOrDefault("")
        val allowedPortal = host == "educacao.sp.gov.br" || host.endsWith(".educacao.sp.gov.br")
        if (!allowedPortal) {
            paused = true
            onNeedUser("Abra a Sala do Futuro. A automação não é injetada em páginas do Gov.br.")
            return
        }
        webView.evaluateJavascript(assetScript) { after() }
    }

    private fun planJson(p: PortalPlan): String {
        val o = JSONObject()
        o.put("date", p.date)
        o.put("className", p.className)
        o.put("subject", p.subject)
        o.put("content", p.content)
        o.put("periodSlots", JSONArray(p.periodSlots))
        o.put("absentees", JSONArray(p.absentees))
        return o.toString()
    }

    private fun decodeJsString(value: String?): String {
        if (value == null || value == "null") return "{}"
        return runCatching { JSONArray("[$value]").getString(0) }.getOrDefault(value)
    }

    private fun detail(o: JSONObject): String {
        val d = o.optJSONObject("detail") ?: return ""
        return "\n\nDetalhes: ${d.toString(2)}"
    }
}
