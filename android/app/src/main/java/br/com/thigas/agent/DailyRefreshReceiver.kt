package br.com.thigas.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class DailyRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProfessorPrefs.autoEnabled(context)) return
        val pending = goAsync()
        thread {
            try {
                val plans = CoreClient().today()
                val count = ProfessorAlarmScheduler.schedulePlans(context, plans)
                if (count == 0) {
                    NotificationHelper.problem(
                        context,
                        "Thigas · agenda",
                        "Nenhuma aula futura foi encontrada para hoje."
                    )
                }
            } catch (_: Exception) {
                NotificationHelper.problem(
                    context,
                    "Thigas · núcleo indisponível",
                    "Não consegui ler a agenda em 127.0.0.1:8765. Mantenha o núcleo Termux em execução."
                )
            } finally {
                ProfessorAlarmScheduler.scheduleDailyRefresh(context)
                pending.finish()
            }
        }
    }
}
