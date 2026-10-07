package br.com.thigas.agent

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
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
    private lateinit var autoSwitch: SwitchCompat
    private val plans = mutableListOf<PortalPlan>()
    private var planIndex = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationHelper.createChannels(this)

        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)
        planText = findViewById(R.id.planText)
        absenteesInput = findViewById(R.id.absenteesInput)
        actionButton = findViewById(R.id.btnAction)
        autoSwitch = findViewById(R.id.autoSwitch)

        val script =
            assets.open("portal_automation.js")
                .bufferedReader()
                .use { it.readText() }

        automation =
            PortalAutomationController(
                webView,
                script,
                ::setStatus,
                ::showNeedUser,
                ::showDone
            )

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.mixedContentMode =
            android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.settings.userAgentString =
            webView.settings.userAgentString +
                " ThigasAgent/0.9"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(
                webView,
                true
            )

        webView.webChromeClient =
            WebChromeClient()

        webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val host =
                        request.url.host
                            ?: return false

                    val allowed =
                        host == "educacao.sp.gov.br" ||
                        host.endsWith(
                            ".educacao.sp.gov.br"
                        ) ||
                        host == "gov.br" ||
                        host.endsWith(
                            ".gov.br"
                        )

                    if (allowed) {
                        return false
                    }

                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            request.url
                        )
                    )

                    return true
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    setStatus(
                        if (
                            url.contains(
                                "educacao.sp.gov.br"
                            )
                        ) {
                            "Sala do Futuro aberta"
                        } else {
                            "Login manual — credenciais não são lidas pelo Thigas"
                        }
                    )
                }
            }

        findViewById<Button>(
            R.id.btnPortal
        ).setOnClickListener {
            webView.loadUrl(
                "https://saladofuturoprofessor.educacao.sp.gov.br/Inicio"
            )
        }

        findViewById<Button>(
            R.id.btnMap
        ).setOnClickListener {
            mapPortal()
        }

        findViewById<Button>(
            R.id.btnAgenda
        ).setOnClickListener {
            loadAgenda()
        }

        findViewById<Button>(
            R.id.btnLinkSession
        ).setOnClickListener {
            linkSession()
        }

        findViewById<Button>(
            R.id.btnTestNow
        ).setOnClickListener {
            testAutomaticNow()
        }

        findViewById<Button>(
            R.id.btnPrevious
        ).setOnClickListener {
            changePlan(-1)
        }

        findViewById<Button>(
            R.id.btnNext
        ).setOnClickListener {
            changePlan(+1)
        }

        actionButton.setOnClickListener {
            if (
                automation.isPaused()
            ) {
                actionButton.text =
                    "Preparar ação"

                automation.resume()
            } else {
                prepareAction()
            }
        }

        autoSwitch.isChecked =
            ProfessorPrefs.autoEnabled(
                this
            )

        autoSwitch.setOnCheckedChangeListener {
                _,
                checked ->

            if (
                checked &&
                SessionVault.load(this) == null
            ) {
                ProfessorPrefs.setAutoEnabled(
                    this,
                    false
                )
                autoSwitch.isChecked = false

                AlertDialog.Builder(this)
                    .setTitle("Vincule a sessão primeiro")
                    .setMessage(
                        "Entre manualmente na Sala do Futuro, abra a visão ADM e toque em “Vincular sessão ADM”. Depois ative o modo automático."
                    )
                    .setPositiveButton("OK", null)
                    .show()

                return@setOnCheckedChangeListener
            }

            ProfessorPrefs.setAutoEnabled(
                this,
                checked
            )

            if (checked) {
                requestNotificationPermission()
                requestExactAlarmAccess()

                ProfessorAlarmScheduler
                    .scheduleDailyRefresh(
                        this
                    )

                setStatus(
                    "Modo automático ativado. Carregando horários…"
                )

                loadAgenda()
            } else {
                setStatus(
                    "Modo automático desativado."
                )
            }
        }

        requestNotificationPermission()

        webView.loadUrl(
            "https://saladofuturoprofessor.educacao.sp.gov.br/Inicio"
        )

        loadAgenda()
    }

    private fun testAutomaticNow() {
        val snapshot =
            SessionVault.load(this)

        if (snapshot == null) {
            AlertDialog.Builder(this)
                .setTitle("Sessão ADM não vinculada")
                .setMessage(
                    "Entre manualmente na Sala do Futuro, abra a visão ADM e toque em “Vincular sessão ADM” antes de testar."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (plans.isEmpty()) {
            Toast.makeText(
                this,
                "Carregue a agenda primeiro.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val plan =
            plans[planIndex].copy(
                content = "",
                absentees = emptyList()
            )

        val existing =
            ProfessorPrefs.activePlan(this)

        if (existing != null) {
            ProfessorPrefs.clearActive(this)
        }

        AlertDialog.Builder(this)
            .setTitle("Testar automático agora")
            .setMessage(
                "O Thigas vai abrir a Sala do Futuro sozinho para ${plan.className} · ${plan.subject}, navegar até a frequência e então perguntar quem faltou. Nada será salvo antes da sua resposta. Continuar?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("TESTAR") { _, _ ->
                ProfessorPrefs.saveActive(
                    this,
                    plan,
                    "open_frequency"
                )

                setStatus(
                    "Teste automático iniciado para ${plan.className}."
                )

                ContextCompat.startForegroundService(
                    this,
                    Intent(
                        this,
                        ProfessorAutomationService::class.java
                    ).apply {
                        action =
                            ProfessorAutomationService.ACTION_START
                    }
                )
            }
            .show()
    }

    private fun linkSession() {
        val currentUrl =
            webView.url.orEmpty()

        val host =
            runCatching {
                Uri.parse(
                    currentUrl
                ).host.orEmpty()
            }.getOrDefault("")

        val portalHost =
            host ==
                "educacao.sp.gov.br" ||
            host.endsWith(
                ".educacao.sp.gov.br"
            )

        if (!portalHost) {
            AlertDialog.Builder(this)
                .setTitle(
                    "Abra a visão ADM"
                )
                .setMessage(
                    "Faça o login manualmente e chegue à visão ADM/Diário de Classe antes de vincular a sessão."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        val cookieManager =
            CookieManager.getInstance()

        cookieManager.flush()

        val cookies =
            cookieManager.getCookie(
                currentUrl
            ).orEmpty()

        val js =
            """
            (function() {
              const local = {};
              const session = {};
              for (let i = 0; i < localStorage.length; i++) {
                const k = localStorage.key(i);
                local[k] = localStorage.getItem(k);
              }
              for (let i = 0; i < sessionStorage.length; i++) {
                const k = sessionStorage.key(i);
                session[k] = sessionStorage.getItem(k);
              }
              return JSON.stringify({
                href: location.href,
                localStorage: local,
                sessionStorage: session,
                body: (document.body && document.body.innerText || '').slice(0, 2500)
              });
            })()
            """.trimIndent()

        webView.evaluateJavascript(
            js
        ) {
                raw ->

            val decoded =
                decodeJsString(
                    raw
                )

            val obj =
                runCatching {
                    JSONObject(
                        decoded
                    )
                }.getOrNull()

            if (obj == null) {
                setStatus(
                    "Não consegui capturar a sessão."
                )
                return@evaluateJavascript
            }

            val local =
                obj.optJSONObject(
                    "localStorage"
                )?.toString()
                    ?: "{}"

            val session =
                obj.optJSONObject(
                    "sessionStorage"
                )?.toString()
                    ?: "{}"

            val href =
                obj.optString(
                    "href",
                    currentUrl
                )

            if (
                cookies.isBlank() &&
                local == "{}" &&
                session == "{}"
            ) {
                AlertDialog.Builder(this)
                    .setTitle(
                        "Sessão não detectada"
                    )
                    .setMessage(
                        "Não encontrei cookies ou tokens de sessão. Confirme que você já está dentro da sua conta e da visão ADM."
                    )
                    .setPositiveButton(
                        "OK",
                        null
                    )
                    .show()

                return@evaluateJavascript
            }

            runCatching {
                SessionVault.save(
                    this,
                    href,
                    cookies,
                    local,
                    session
                )
            }.onSuccess {
                cookieManager.flush()

                setStatus(
                    "Sessão ADM vinculada e criptografada."
                )

                AlertDialog.Builder(this)
                    .setTitle(
                        "Sessão vinculada"
                    )
                    .setMessage(
                        "O Thigas salvou a sessão autenticada localmente e criptografada. Login e senha não foram armazenados. Agora você pode ativar o modo automático."
                    )
                    .setPositiveButton(
                        "OK",
                        null
                    )
                    .show()
            }.onFailure {
                setStatus(
                    "Falha ao criptografar a sessão."
                )
            }
        }
    }

    private fun decodeJsString(
        value: String?
    ): String {
        if (
            value == null ||
            value == "null"
        ) {
            return "{}"
        }

        return runCatching {
            JSONArray(
                "[$value]"
            ).getString(0)
        }.getOrDefault(
            value
        )
    }

    private fun loadAgenda() {
        setStatus(
            "Lendo agenda do núcleo local…"
        )

        thread {
            val result =
                runCatching {
                    CoreClient().today()
                }

            runOnUiThread {
                result.onSuccess {
                    plans.clear()
                    plans.addAll(it)
                    planIndex = 0
                    renderPlan()

                    if (
                        ProfessorPrefs.autoEnabled(
                            this
                        )
                    ) {
                        val scheduled =
                            ProfessorAlarmScheduler
                                .schedulePlans(
                                    this,
                                    plans
                                )

                        setStatus(
                            "Agenda carregada: ${plans.size} blocos · " +
                                "$scheduled agendados automaticamente"
                        )
                    } else {
                        setStatus(
                            "Agenda carregada: ${plans.size} blocos de aula"
                        )
                    }
                }.onFailure {
                    setStatus(
                        "Núcleo local indisponível. Inicie o Thigas no Termux em 127.0.0.1:8765"
                    )
                }
            }
        }
    }

    private fun changePlan(
        delta: Int
    ) {
        if (
            plans.isEmpty()
        ) {
            return
        }

        planIndex =
            (
                planIndex +
                    delta
            ).coerceIn(
                0,
                plans.lastIndex
            )

        renderPlan()
    }

    private fun renderPlan() {
        if (
            plans.isEmpty()
        ) {
            planText.text =
                "Nenhuma aula carregada"

            return
        }

        val p =
            plans[
                planIndex
            ]

        planText.text =
            "${planIndex + 1}/${plans.size} · ${p.className} · ${p.subject}\n" +
                p.periodSlots.joinToString(
                    " | "
                ) +
                "\n" +
                p.content
    }

    private fun prepareAction() {
        if (
            plans.isEmpty()
        ) {
            Toast.makeText(
                this,
                "Carregue a agenda primeiro",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val base =
            plans[
                planIndex
            ]

        val absentees =
            absenteesInput.text
                .toString()
                .split(
                    ',',
                    ';',
                    '\n'
                )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }

        val p =
            base.copy(
                absentees =
                    absentees
            )

        val faltas =
            if (
                absentees.isEmpty()
            ) {
                "ninguém"
            } else {
                absentees.joinToString(
                    ", "
                )
            }

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "Confirmar lançamento"
            )
            .setMessage(
                "Turma: ${p.className}\n" +
                    "Disciplina: ${p.subject}\n" +
                    "Horários: ${p.periodSlots.joinToString()}\n" +
                    "Faltosos: $faltas\n\n" +
                    "Conteúdo:\n${p.content}\n\n" +
                    "Confirma?"
            )
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "CONFIRMAR"
            ) {
                    _,
                    _ ->

                automation.start(
                    p
                )
            }
            .show()
    }

    private fun mapPortal() {
        automation.map {
                json ->

            val stamp =
                SimpleDateFormat(
                    "yyyyMMdd-HHmmss",
                    Locale.US
                ).format(
                    Date()
                )

            val f =
                File(
                    filesDir,
                    "portal-map-$stamp.json"
                )

            f.writeText(
                json
            )

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Mapa da tela salvo"
                )
                .setMessage(
                    "${f.absolutePath}\n\n" +
                        "O mapa contém nomes técnicos dos elementos da tela, não sua senha."
                )
                .setNeutralButton(
                    "Copiar mapa"
                ) {
                        _,
                        _ ->

                    val cm =
                        getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager

                    cm.setPrimaryClip(
                        android.content.ClipData
                            .newPlainText(
                                "Thigas portal map",
                                json
                            )
                    )

                    Toast.makeText(
                        this,
                        "Mapa copiado",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >=
                33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                4901
            )
        }
    }

    private fun requestExactAlarmAccess() {
        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.S
        ) {
            return
        }

        val alarm =
            getSystemService(
                AlarmManager::class.java
            )

        if (
            alarm.canScheduleExactAlarms()
        ) {
            return
        }

        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                ).apply {
                    data =
                        Uri.parse(
                            "package:$packageName"
                        )
                }
            )
        }
    }

    private fun setStatus(
        s: String
    ) {
        runOnUiThread {
            statusText.text =
                s
        }
    }

    private fun showNeedUser(
        s: String
    ) {
        runOnUiThread {
            actionButton.text =
                "Continuar"

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Thigas precisa de você"
                )
                .setMessage(
                    s +
                        "\n\nFaça o ajuste necessário no portal e toque em Continuar."
                )
                .setPositiveButton(
                    "Entendi",
                    null
                )
                .show()
        }
    }

    private fun showDone(
        s: String
    ) {
        runOnUiThread {
            actionButton.text =
                "Preparar ação"

            setStatus(
                s
            )

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Concluído"
                )
                .setMessage(
                    s
                )
                .setPositiveButton(
                    "Próxima turma"
                ) {
                        _,
                        _ ->

                    changePlan(
                        +1
                    )
                }
                .show()
        }
    }

    override fun onBackPressed() {
        if (
            webView.canGoBack()
        ) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
