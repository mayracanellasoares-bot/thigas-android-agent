package br.com.thigas.agent

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CoreClient(
    private val baseUrl: String =
        "http://127.0.0.1:8765"
) {
    private val timeRange =
        Regex(
            """\b\d{1,2}:\d{2}\s*[-–—]\s*\d{1,2}:\d{2}\b"""
        )

    fun today(): List<PortalPlan> {
        val conn =
            URL(
                "$baseUrl/api/professor/today"
            ).openConnection() as HttpURLConnection

        conn.connectTimeout = 2500
        conn.readTimeout = 3500
        conn.requestMethod = "GET"

        conn.inputStream
            .bufferedReader()
            .use { reader ->
                val root =
                    JSONObject(
                        reader.readText()
                    )

                val arr =
                    root.getJSONArray(
                        "blocks"
                    )

                return (
                    0 until arr.length()
                ).map { i ->
                    val b =
                        arr.getJSONObject(i)

                    val rawSlots =
                        mutableListOf<String>()

                    val a =
                        b.optJSONArray(
                            "period_slots"
                        )

                    if (a != null) {
                        for (
                            j in 0 until a.length()
                        ) {
                            rawSlots +=
                                a.optString(j)
                        }
                    }

                    val normalized =
                        rawSlots
                            .flatMap { raw ->
                                timeRange
                                    .findAll(raw)
                                    .map { it.value }
                                    .toList()
                                    .ifEmpty {
                                        listOf(raw)
                                    }
                            }
                            .map {
                                it.replace(
                                    '–',
                                    '-'
                                )
                                    .replace(
                                        '—',
                                        '-'
                                    )
                                    .replace(
                                        Regex(
                                            """\s*-\s*"""
                                        ),
                                        " - "
                                    )
                                    .trim()
                            }
                            .filter {
                                it.isNotBlank()
                            }
                            .distinct()
                            .toMutableList()

                    if (
                        normalized.isEmpty()
                    ) {
                        val start =
                            b.optString(
                                "start"
                            )
                        val end =
                            b.optString(
                                "end"
                            )

                        if (
                            start.isNotBlank() &&
                            end.isNotBlank()
                        ) {
                            normalized +=
                                "$start - $end"
                        }
                    }

                    PortalPlan(
                        date =
                            b.optString(
                                "date"
                            ),
                        className =
                            b.optString(
                                "class_name"
                            ),
                        subject =
                            b.optString(
                                "subject"
                            ),
                        content =
                            b.optString(
                                "content"
                            ),
                        periodSlots =
                            normalized
                    )
                }
            }
    }
}
