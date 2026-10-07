package br.com.thigas.agent

import android.content.Context

object ProfessorPrefs {
    private const val PREFS = "thigas_professor_auto"
    private const val AUTO = "auto_enabled"
    private const val ACTIVE_PLAN = "active_plan"
    private const val ACTIVE_STATE = "active_state"

    fun autoEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO, false)

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(AUTO, enabled)
            .apply()
    }

    fun saveActive(context: Context, plan: PortalPlan, state: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACTIVE_PLAN, PortalPlanCodec.toJson(plan))
            .putString(ACTIVE_STATE, state)
            .apply()
    }

    fun activePlan(context: Context): PortalPlan? =
        PortalPlanCodec.fromJson(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(ACTIVE_PLAN, null)
        )

    fun activeState(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_STATE, null)

    fun clearActive(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(ACTIVE_PLAN)
            .remove(ACTIVE_STATE)
            .apply()
    }
}
