package br.com.thigas.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

class LessonReplyReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REPLY = "br.com.thigas.agent.LESSON_REPLY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val answer = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_LESSON)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (answer.isBlank()) return

        val plan = ProfessorPrefs.activePlan(context) ?: return
        val content = ReplyParser.lesson(answer)
        if (content.isBlank()) return

        ProfessorPrefs.saveActive(
            context,
            plan.copy(content = content),
            "lesson_form"
        )

        ContextCompat.startForegroundService(
            context,
            Intent(context, ProfessorAutomationService::class.java).apply {
                action = ProfessorAutomationService.ACTION_RESUME
            }
        )
    }
}
