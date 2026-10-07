package br.com.thigas.agent

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var planText: TextView
    private lateinit var absenteesInput: EditText
    private lateinit var automation: PortalAutomationController
    private lateinit var actionButton: Button
    private val plans = mutableListOf<PortalPlan>()
    private var planIndex = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)
        planText = findViewById(R.id.planText)
        absenteesInput = findViewById(R.id.absenteesInput)
        actionButton = findViewById(R.id.btnAction)

        val script = assets.open("portal_automation.js").bufferedReader().use { it.readText() }
        automation = PortalAutomationController(webView, script, ::setStatus, ::showNeedUser, ::showDone)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.settings.userAgentString = webView.settings.userAgentString + " ThigasAgent/0.8"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return false
                val allowed = host == "educacao.sp.gov.br" || host.endsWith(".educacao.sp.gov.br") || host == "gov.br" || host.endsWith(".gov.br")
                if (allowed) return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                setStatus(if (url.contains("educacao.sp.gov.br")) "Sala do Futuro aberta" else "Login manual — credenciais não são lidas pelo Thigas")
            }
        }

        findViewById<Button>(R.id.btnPortal).setOnClickListener {
            webView.loadUrl("https://saladofuturoprofessor.educacao.sp.gov.br/Inicio")
        }
        findViewById<Button>(R.id.btnMap).setOnClickListener { mapPortal() }
        findViewById<Button>(R.id.btnAgenda).setOnClickListener { loadAgenda() }
        findViewById<Button>(R.id.btnPrevious).setOnClickListener { changePlan(-1) }
        findViewById<Button>(R.id.btnNext).setOnClickListener { changePlan(+1) }
        actionButton.setOnClickListener {
            if (automation.isPaused()) {
                actionButton.text = "Preparar ação"
                automation.resume()
            } else prepareAction()
        }

        webView.loadUrl("https://saladofuturoprofessor.educacao.sp.gov.br/Inicio")
        loadAgenda()
    }

    private fun loadAgenda() {
        setStatus("Lendo agenda do núcleo local…")
        thread {
            val result = runCatching { CoreClient().today() }
            runOnUiThread {
                result.onSuccess {
                    plans.clear(); plans.addAll(it); planIndex = 0; renderPlan()
                    setStatus("Agenda carregada: ${plans.size} blocos de aula")
                }.onFailure {
                    setStatus("Núcleo local indisponível. Inicie a v0.8 no Termux em 127.0.0.1:8765")
                }
            }
        }
    }

    private fun changePlan(delta: Int) {
        if (plans.isEmpty()) return
        planIndex = (planIndex + delta).coerceIn(0, plans.lastIndex)
        renderPlan()
    }

    private fun renderPlan() {
        if (plans.isEmpty()) { planText.text = "Nenhuma aula carregada"; return }
        val p = plans[planIndex]
        planText.text = "${planIndex+1}/${plans.size} · ${p.className} · ${p.subject}\n${p.periodSlots.joinToString(" | ")}\n${p.content}"
    }

    private fun prepareAction() {
        if (plans.isEmpty()) { Toast.makeText(this,"Carregue a agenda primeiro",Toast.LENGTH_SHORT).show(); return }
        val base = plans[planIndex]
        val absentees = absenteesInput.text.toString().split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }
        val p = base.copy(absentees = absentees)
        val faltas = if (absentees.isEmpty()) "ninguém" else absentees.joinToString(", ")
        AlertDialog.Builder(this)
            .setTitle("Confirmar lançamento")
            .setMessage("Turma: ${p.className}\nDisciplina: ${p.subject}\nHorários: ${p.periodSlots.joinToString()}\nFaltosos: $faltas\n\nConteúdo:\n${p.content}\n\nO Thigas poderá clicar em Salvar na Sala do Futuro. Confirma?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("CONFIRMAR") { _, _ -> automation.start(p) }
            .show()
    }

    private fun mapPortal() {
        automation.map { json ->
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val f = File(filesDir, "portal-map-$stamp.json")
            f.writeText(json)
            AlertDialog.Builder(this)
                .setTitle("Mapa da tela salvo")
                .setMessage("${f.absolutePath}\n\nO mapa contém nomes técnicos dos elementos da tela, não sua senha.")
                .setNeutralButton("Copiar mapa") { _, _ ->
                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Thigas portal map", json))
                    Toast.makeText(this, "Mapa copiado", Toast.LENGTH_SHORT).show()
                }
                .setPositiveButton("OK", null).show()
        }
    }

    private fun setStatus(s: String) { runOnUiThread { statusText.text = s } }

    private fun showNeedUser(s: String) {
        runOnUiThread {
            actionButton.text = "Continuar"
            AlertDialog.Builder(this).setTitle("Thigas precisa de você").setMessage(s + "\n\nFaça o ajuste necessário no portal e toque em Continuar.")
                .setPositiveButton("Entendi", null).show()
        }
    }

    private fun showDone(s: String) {
        runOnUiThread {
            actionButton.text = "Preparar ação"
            setStatus(s)
            AlertDialog.Builder(this).setTitle("Concluído").setMessage(s)
                .setPositiveButton("Próxima turma") { _, _ -> changePlan(+1) }.show()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
