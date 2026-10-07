package br.com.thigas.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

class AttendanceReplyReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REPLY = "br.com.thigas.agent.ATTENDANCE_REPLY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val answer = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_ATTENDANCE)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (answer.isBlank()) return

        val plan = ProfessorPrefs.activePlan(context) ?: return
        val updated = plan.copy(absentees = ReplyParser.absentees(answer))
        ProfessorPrefs.saveActive(context, updated, "attendance_students")

        ContextCompat.startForegroundService(
            context,
            Intent(context, ProfessorAutomationService::class.java).apply {
                action = ProfessorAutomationService.ACTION_RESUME
            }
        )
    }
}
