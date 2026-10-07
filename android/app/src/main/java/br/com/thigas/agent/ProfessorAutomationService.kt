package br.com.thigas.agent

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class ProfessorAutomationService : Service() {
    companion object {
        const val ACTION_START = "br.com.thigas.agent.AUTO_START"
        const val ACTION_RESUME = "br.com.thigas.agent.AUTO_RESUME"
        private const val PORTAL =
            "https://saladofuturoprofessor.educacao.sp.gov.br/Inicio"
        private const val WAIT_ATTENDANCE = "WAIT_ATTENDANCE"
        private const val WAIT_LESSON = "WAIT_LESSON"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private lateinit var webView: WebView
    private lateinit var script: String
    private var wakeLock: PowerManager.WakeLock? = null
    private var pageReady = false

    override fun onCreate() {
        super.onCreate()

        NotificationHelper.createChannels(this)
        startForeground(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            NotificationHelper.serviceNotification(
                this,
                "Preparando a Sala do Futuro…"
            )
        )

        acquireWakeLock()
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        script = assets.open("portal_automation.js")
            .bufferedReader()
            .use { it.readText() }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode =
                android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString =
                settings.userAgentString + " ThigasAgent/0.9-headless"

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(this, true)

            webChromeClient = WebChromeClient()

            measure(
                View.MeasureSpec.makeMeasureSpec(
                    1080,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    1920,
                    View.MeasureSpec.EXACTLY
                )
            )
            layout(0, 0, 1080, 1920)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    val host = runCatching {
                        android.net.Uri.parse(url).host.orEmpty()
                    }.getOrDefault("")

                    val portalHost =
                        host == "educacao.sp.gov.br" ||
                        host.endsWith(".educacao.sp.gov.br")

                    if (!portalHost) {
                        fail(
                            "Thigas · sessão expirada",
                            "A Sala do Futuro pediu login novamente. Abra o app, faça o login manualmente e o próximo horário voltará a funcionar."
                        )
                        return
                    }

                    pageReady = true
                    handler.postDelayed(
                        { runStoredState() },
                        1600
                    )
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val plan = ProfessorPrefs.activePlan(this)

        if (plan == null) {
            stopSafely()
            return START_NOT_STICKY
        }

        val action = intent?.action ?: ACTION_RESUME

        if (
            action == ACTION_START ||
            !pageReady ||
            webView.url.isNullOrBlank()
        ) {
            pageReady = false
            updateForeground(
                "Entrando na Sala do Futuro para ${plan.className}…"
            )
            webView.loadUrl(PORTAL)
        } else {
            runStoredState()
        }

        return START_NOT_STICKY
    }

    private fun runStoredState() {
        if (!pageReady || busy.get()) return

        val plan = ProfessorPrefs.activePlan(this) ?: run {
            stopSafely()
            return
        }

        when (
            val state =
                ProfessorPrefs.activeState(this)
                    ?: "open_frequency"
        ) {
            WAIT_ATTENDANCE -> {
                updateForeground(
                    "Aguardando quem faltou em ${plan.className}"
                )
                NotificationHelper.attendanceQuestion(
                    this,
                    plan
                )
            }

            WAIT_LESSON -> {
                updateForeground(
                    "Aguardando o conteúdo de ${plan.className}"
                )
                NotificationHelper.lessonQuestion(
                    this,
                    plan
                )
            }

            else -> runPhase(
                plan,
                state
            )
        }
    }

    private fun runPhase(
        plan: PortalPlan,
        phase: String
    ) {
        if (!busy.compareAndSet(false, true)) return

        val payload =
            PortalPlanCodec.toJson(plan)

        webView.evaluateJavascript(script) {
            val js =
                "JSON.stringify(window.ThigasPortal.step(" +
                    payload +
                    "," +
                    JSONObject.quote(phase) +
                    "))"

            webView.evaluateJavascript(js) { raw ->
                busy.set(false)

                val decoded =
                    decodeJsString(raw)

                val obj =
                    runCatching {
                        JSONObject(decoded)
                    }.getOrNull()

                if (obj == null) {
                    fail(
                        "Thigas · portal mudou",
                        "A resposta do portal não pôde ser interpretada. Nenhum novo dado foi salvo."
                    )
                    return@evaluateJavascript
                }

                val message =
                    obj.optString(
                        "message",
                        phase
                    )

                updateForeground(message)

                if (
                    obj.optBoolean(
                        "done",
                        false
                    )
                ) {
                    NotificationHelper.done(
                        this,
                        plan,
                        message.ifBlank {
                            "Frequência e registro de aula salvos."
                        }
                    )

                    ProfessorPrefs.clearActive(this)
                    stopSafely()
                    return@evaluateJavascript
                }

                if (
                    obj.optBoolean(
                        "needsUser",
                        false
                    )
                ) {
                    val detail =
                        obj.optJSONObject("detail")
                            ?.toString()
                            .orEmpty()

                    fail(
                        "Thigas precisa de você",
                        message +
                            if (detail.isBlank()) {
                                ""
                            } else {
                                "\n" + detail
                            }
                    )

                    return@evaluateJavascript
                }

                val next =
                    obj.optString("nextPhase")

                if (next.isBlank()) {
                    handler.postDelayed(
                        {
                            runPhase(
                                plan,
                                phase
                            )
                        },
                        1300
                    )
                    return@evaluateJavascript
                }

                if (next == "attendance_students") {
                    ProfessorPrefs.saveActive(
                        this,
                        plan,
                        WAIT_ATTENDANCE
                    )

                    updateForeground(
                        "Sala pronta. Aguardando faltosos…"
                    )

                    NotificationHelper.attendanceQuestion(
                        this,
                        plan
                    )

                    return@evaluateJavascript
                }

                if (next == "lesson_form") {
                    ProfessorPrefs.saveActive(
                        this,
                        plan,
                        WAIT_LESSON
                    )

                    updateForeground(
                        "Frequência salva. Aguardando conteúdo…"
                    )

                    NotificationHelper.lessonQuestion(
                        this,
                        plan
                    )

                    return@evaluateJavascript
                }

                ProfessorPrefs.saveActive(
                    this,
                    plan,
                    next
                )

                handler.postDelayed(
                    {
                        runPhase(
                            plan,
                            next
                        )
                    },
                    if (
                        obj.optBoolean(
                            "waiting",
                            false
                        )
                    ) {
                        1600
                    } else {
                        1100
                    }
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
        ) return "{}"

        return runCatching {
            JSONArray("[$value]")
                .getString(0)
        }.getOrDefault(value)
    }

    private fun updateForeground(
        message: String
    ) {
        getSystemService(
            android.app.NotificationManager::class.java
        ).notify(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            NotificationHelper.serviceNotification(
                this,
                message
            )
        )
    }

    private fun fail(
        title: String,
        message: String
    ) {
        NotificationHelper.problem(
            this,
            title,
            message
        )

        ProfessorPrefs.clearActive(this)
        stopSafely()
    }

    private fun acquireWakeLock() {
        val pm =
            getSystemService(
                PowerManager::class.java
            )

        wakeLock =
            pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ThigasAgent:ProfessorAutomation"
            ).apply {
                setReferenceCounted(false)
                acquire(
                    90 * 60 * 1000L
                )
            }
    }

    private fun stopSafely() {
        if (::webView.isInitialized) {
            runCatching {
                webView.stopLoading()
            }
            runCatching {
                webView.destroy()
            }
        }

        if (
            wakeLock?.isHeld == true
        ) {
            wakeLock?.release()
        }

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {
        if (
            ::webView.isInitialized
        ) {
            runCatching {
                webView.destroy()
            }
        }

        if (
            wakeLock?.isHeld == true
        ) {
            wakeLock?.release()
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
