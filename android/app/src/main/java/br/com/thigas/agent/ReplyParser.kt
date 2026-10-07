package br.com.thigas.agent

object ReplyParser {
    fun absentees(text: String): List<String> {
        val raw = text.trim()
        val normalized = raw.lowercase()
            .replace("á","a").replace("à","a").replace("ã","a").replace("â","a")
            .replace("é","e").replace("ê","e").replace("í","i")
            .replace("ó","o").replace("ô","o").replace("õ","o")
            .replace("ú","u").replace("ç","c")

        if (
            normalized == "ninguem" ||
            normalized.contains("ninguem faltou") ||
            normalized.contains("nenhum faltou") ||
            normalized.contains("nenhuma faltou") ||
            normalized.contains("todos presentes") ||
            normalized.contains("sem faltas")
        ) return emptyList()

        val cleaned = raw
            .replace(
                Regex(
                    """(?i)^\s*(a|o|as|os)?\s*(aluna|aluno|alunas|alunos)?\s*(que\s+)?(faltou|faltaram|faltoso|faltosos|faltosa|faltosas|ausente|ausentes)\s*(é|e|são|sao|:|-)?\s*"""
                ),
                ""
            )
            .trim()

        return cleaned
            .split(Regex("""\s*(?:,|;|\be\b)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun lesson(text: String): String =
        text.trim()
            .replace(Regex("""(?i)^\s*(conte[uú]do|aula|registro)\s*:\s*"""), "")
            .trim()
}
