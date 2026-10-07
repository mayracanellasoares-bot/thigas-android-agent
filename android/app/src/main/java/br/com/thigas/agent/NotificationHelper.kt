package br.com.thigas.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

object NotificationHelper {
    const val CLASS_CHANNEL = "thigas_classes"
    const val SERVICE_CHANNEL = "thigas_service"
    const val KEY_ATTENDANCE = "attendance_reply"
    const val KEY_LESSON = "lesson_reply"
    const val SERVICE_NOTIFICATION_ID = 8100

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CLASS_CHANNEL,
                "Aulas do Thigas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Perguntas de frequência, conteúdo e resultado dos lançamentos"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL,
                "Automação em andamento",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o lançamento da aula ativo enquanto o Thigas trabalha"
            }
        )
    }

    fun serviceNotification(context: Context, message: String): Notification =
        NotificationCompat.Builder(context, SERVICE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Thigas · Sala do Futuro")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun attendanceQuestion(context: Context, plan: PortalPlan) {
        val remoteInput = RemoteInput.Builder(KEY_ATTENDANCE)
            .setLabel("Ex.: Ágatha, João — ou ninguém")
            .build()
        val pending = PendingIntent.getBroadcast(
            context,
            plan.key().hashCode() and 0x7fffffff,
            Intent(context, AttendanceReplyReceiver::class.java).apply {
                action = AttendanceReplyReceiver.ACTION_REPLY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Responder faltosos",
            pending
        ).addRemoteInput(remoteInput).build()

        notify(
            context,
            8200,
            NotificationCompat.Builder(context, CLASS_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${plan.className} · ${plan.subject}")
                .setContentText("Quem faltou nesta aula? Responda “ninguém” se todos vieram.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "A Sala do Futuro está pronta para ${plan.className}. Quem faltou? Digite os nomes separados por vírgula, ou responda “ninguém”."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(false)
                .addAction(action)
                .build()
        )
    }

    fun lessonQuestion(context: Context, plan: PortalPlan) {
        val remoteInput = RemoteInput.Builder(KEY_LESSON)
            .setLabel("Conteúdo dado na aula")
            .build()
        val pending = PendingIntent.getBroadcast(
            context,
            (plan.key() + "|lesson").hashCode() and 0x7fffffff,
            Intent(context, LessonReplyReceiver::class.java).apply {
                action = LessonReplyReceiver.ACTION_REPLY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "Informar conteúdo",
            pending
        ).addRemoteInput(remoteInput).build()

        notify(
            context,
            8201,
            NotificationCompat.Builder(context, CLASS_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${plan.className} · frequência salva")
                .setContentText("O que foi dado na aula?")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "A frequência foi salva. Agora informe o conteúdo da aula; o Thigas registrará e salvará automaticamente."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(false)
                .addAction(action)
                .build()
        )
    }

    fun done(context: Context, plan: PortalPlan, message: String) {
        notify(
            context,
            8202,
            NotificationCompat.Builder(context, CLASS_CHANNEL)
                .setSmallIcon(android.R.drawable.checkbox_on_background)
                .setContentTitle("${plan.className} · concluído")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    fun problem(context: Context, title: String, message: String) {
        val openIntent = PendingIntent.getActivity(
            context,
            8300,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notify(
            context,
            8300,
            NotificationCompat.Builder(context, CLASS_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
