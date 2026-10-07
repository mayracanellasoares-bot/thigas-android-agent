package br.com.thigas.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ProfessorAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProfessorPrefs.autoEnabled(context)) return
        val plan = PortalPlanCodec.fromJson(intent.getStringExtra("plan_json")) ?: return

        val active = ProfessorPrefs.activePlan(context)
        if (active != null && active.key() != plan.key()) {
            NotificationHelper.problem(
                context,
                "Thigas · aula aguardando",
                "Ainda existe um lançamento em andamento. A aula ${plan.className} não será gravada até o lançamento anterior terminar."
            )
            return
        }

        ProfessorPrefs.saveActive(
            context,
            plan.copy(content = "", absentees = emptyList()),
            "open_frequency"
        )
        NotificationHelper.createChannels(context)
        ContextCompat.startForegroundService(
            context,
            Intent(context, ProfessorAutomationService::class.java).apply {
                action = ProfessorAutomationService.ACTION_START
            }
        )
    }
}
