package com.codelock.app.domain

import kotlin.random.Random

/** Estadísticas de una pregunta, calculadas desde el historial de respuestas. */
data class QuestionStats(
    val questionId: Long,
    val timesAsked: Int,
    val timesWrong: Int,
    val lastAskedAt: Long?,
)

/**
 * Elige la siguiente pregunta (reglas puras, sin Android, fáciles de probar):
 * - Evita repetir las últimas preguntas mostradas.
 * - Prioriza las que nunca se han preguntado.
 * - Repite más las que fallaste.
 * - Las que no ves hace días vuelven a ganar peso.
 */
object QuestionPicker {
    const val RECENT_WINDOW = 10
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun weight(stats: QuestionStats, now: Long): Double {
        if (stats.timesAsked == 0) return 4.0
        val errorRate = stats.timesWrong.toDouble() / stats.timesAsked
        val daysSince = stats.lastAskedAt?.let { ((now - it).coerceAtLeast(0) / DAY_MS.toDouble()) } ?: 7.0
        return 1.0 + errorRate * 4.0 + daysSince.coerceAtMost(7.0) / 7.0 * 2.0
    }

    fun pick(
        candidates: List<QuestionStats>,
        recentIds: List<Long>,
        now: Long,
        random: Random = Random.Default,
    ): Long? {
        if (candidates.isEmpty()) return null
        val recent = recentIds.toSet()
        val pool = candidates.filter { it.questionId !in recent }.ifEmpty {
            // Hay pocas preguntas: al menos no repetir la última.
            candidates.filter { it.questionId != recentIds.firstOrNull() }.ifEmpty { candidates }
        }
        val weights = pool.map { weight(it, now) }
        var target = random.nextDouble() * weights.sum()
        for ((index, w) in weights.withIndex()) {
            target -= w
            if (target <= 0) return pool[index].questionId
        }
        return pool.last().questionId
    }
}

/** Cuándo mostrar una pregunta al desbloquear. */
object FrequencyPolicy {
    fun shouldAsk(enabled: Boolean, lastShownAt: Long?, now: Long, minIntervalMinutes: Int): Boolean {
        if (!enabled) return false
        if (lastShownAt == null) return true
        return now - lastShownAt >= minIntervalMinutes * 60_000L
    }
}

/** Resultado guardado de una respuesta. */
enum class AnswerResult(val code: Int) {
    CORRECT(1),
    WRONG(0),
    SKIPPED(-1),
}
