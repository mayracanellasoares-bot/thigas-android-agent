package br.com.thigas.agent

data class PortalPlan(
    val date: String,
    val className: String,
    val subject: String,
    val content: String,
    val periodSlots: List<String>,
    val absentees: List<String> = emptyList()
)
