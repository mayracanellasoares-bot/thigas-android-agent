package br.com.thigas.agent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ProfessorAlarmScheduler {
    private val timeRegex = Regex("""(\d{1,2}):(\d{2})""")

    fun schedulePlans(context: Context, plans: List<PortalPlan>): Int {
        if (!ProfessorPrefs.autoEnabled(context)) return 0
        var scheduled = 0
        val now = System.currentTimeMillis()

        for (plan in plans) {
            val window = classWindow(plan) ?: continue
            val trigger = when {
                window.first > now + 15_000 -> window.first
                window.second > now -> now + 5_000
                else -> continue
            }
            schedulePlan(context, plan, trigger)
            scheduled++
        }
        scheduleDailyRefresh(context)
        return scheduled
    }

    fun scheduleDailyRefresh(context: Context) {
        if (!ProfessorPrefs.autoEnabled(context)) return
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(now.toLocalDate(), LocalTime.of(5, 20))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val whenMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val pending = PendingIntent.getBroadcast(
            context,
            90520,
            Intent(context, DailyRefreshReceiver::class.java)
                .setAction("br.com.thigas.agent.DAILY_REFRESH"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, whenMs, pending)
    }

    private fun schedulePlan(context: Context, plan: PortalPlan, whenMs: Long) {
        val intent = Intent(context, ProfessorAlarmReceiver::class.java).apply {
            action = "br.com.thigas.agent.CLASS_ALARM"
            putExtra("plan_json", PortalPlanCodec.toJson(plan))
        }
        val pending = PendingIntent.getBroadcast(
            context,
            plan.key().hashCode() and 0x7fffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, whenMs, pending)
    }

    private fun setAlarm(context: Context, whenMs: Long, pending: PendingIntent) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, whenMs, pending)
        }
    }

    private fun classWindow(plan: PortalPlan): Pair<Long, Long>? {
        if (plan.periodSlots.isEmpty()) return null
        val firstTimes = timeRegex.findAll(plan.periodSlots.first()).map { it.value }.toList()
        val lastTimes = timeRegex.findAll(plan.periodSlots.last()).map { it.value }.toList()
        if (firstTimes.isEmpty() || lastTimes.isEmpty()) return null

        val date = parseDate(plan.date) ?: LocalDate.now()
        val start = LocalTime.parse(firstTimes.first().padStart(5, '0'))
        val end = LocalTime.parse(lastTimes.last().padStart(5, '0'))
        val zone = ZoneId.systemDefault()
        val startMs = LocalDateTime.of(date, start).atZone(zone).toInstant().toEpochMilli()
        val endMs = LocalDateTime.of(date, end).atZone(zone).toInstant().toEpochMilli()
        return startMs to endMs
    }

    private fun parseDate(raw: String): LocalDate? {
        if (raw.isBlank()) return null
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )
        for (format in formats) {
            val parsed = runCatching { LocalDate.parse(raw, format) }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
