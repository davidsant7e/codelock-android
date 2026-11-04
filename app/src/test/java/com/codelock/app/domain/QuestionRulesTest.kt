package com.codelock.app.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionRulesTest {

    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun stats(id: Long, asked: Int = 0, wrong: Int = 0, lastAsked: Long? = null) =
        QuestionStats(id, asked, wrong, lastAsked)

    // ------------------------------------------------------------ frecuencia
    @Test
    fun `no pregunta si esta desactivado`() {
        assertFalse(FrequencyPolicy.shouldAsk(enabled = false, lastShownAt = null, now = now, minIntervalMinutes = 0))
    }

    @Test
    fun `pregunta la primera vez`() {
        assertTrue(FrequencyPolicy.shouldAsk(true, null, now, 30))
    }

    @Test
    fun `respeta el intervalo minimo`() {
        assertFalse(FrequencyPolicy.shouldAsk(true, now - 4 * 60_000, now, 5))
        assertTrue(FrequencyPolicy.shouldAsk(true, now - 5 * 60_000, now, 5))
    }

    @Test
    fun `intervalo cero pregunta en cada desbloqueo`() {
        assertTrue(FrequencyPolicy.shouldAsk(true, now, now, 0))
    }

    // --------------------------------------------------------------- pesos
    @Test
    fun `las nuevas pesan mas que las acertadas recientes`() {
        val nueva = QuestionPicker.weight(stats(1), now)
        val acertadaHoy = QuestionPicker.weight(stats(2, asked = 3, wrong = 0, lastAsked = now), now)
        assertTrue(nueva > acertadaHoy)
    }

    @Test
    fun `las falladas pesan mas que las acertadas`() {
        val fallada = QuestionPicker.weight(stats(1, asked = 2, wrong = 2, lastAsked = now), now)
        val acertada = QuestionPicker.weight(stats(2, asked = 2, wrong = 0, lastAsked = now), now)
        assertTrue(fallada > acertada)
    }

    @Test
    fun `el tiempo sin verla aumenta el peso hasta una semana`() {
        val ayer = QuestionPicker.weight(stats(1, asked = 1, lastAsked = now - day), now)
        val semana = QuestionPicker.weight(stats(1, asked = 1, lastAsked = now - 7 * day), now)
        val mes = QuestionPicker.weight(stats(1, asked = 1, lastAsked = now - 30 * day), now)
        assertTrue(semana > ayer)
        assertEquals(semana, mes, 0.0001)
    }

    // ------------------------------------------------------------- elección
    @Test
    fun `sin candidatas no hay pregunta`() {
        assertNull(QuestionPicker.pick(emptyList(), emptyList(), now))
    }

    @Test
    fun `no repite las recientes si hay otras`() {
        val candidates = (1L..20L).map { stats(it) }
        val recent = (1L..10L).toList()
        repeat(200) { seed ->
            val picked = QuestionPicker.pick(candidates, recent, now, Random(seed))!!
            assertFalse(picked in recent)
        }
    }

    @Test
    fun `con pocas preguntas al menos no repite la ultima`() {
        val candidates = listOf(stats(1), stats(2))
        repeat(50) { seed ->
            assertNotEquals(2L, QuestionPicker.pick(candidates, listOf(2L, 1L), now, Random(seed)))
        }
    }

    @Test
    fun `con una sola pregunta la repite`() {
        assertEquals(7L, QuestionPicker.pick(listOf(stats(7)), listOf(7L), now, Random(1)))
    }

    @Test
    fun `elige mas seguido las preguntas con mas peso`() {
        val candidates = listOf(
            stats(1, asked = 5, wrong = 5, lastAsked = now - 7 * day), // fallada y olvidada
            stats(2, asked = 5, wrong = 0, lastAsked = now), // acertada hoy
        )
        val counts = (0 until 2000).groupingBy {
            QuestionPicker.pick(candidates, emptyList(), now, Random(it))
        }.eachCount()
        assertTrue((counts[1L] ?: 0) > (counts[2L] ?: 0) * 3)
    }
}
