package br.com.thigas.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ProfessorPrefs.autoEnabled(context)) {
            ProfessorAlarmScheduler.scheduleDailyRefresh(context)
        }
    }
}
