package com.codelock.app.data

import com.codelock.app.data.db.AnswerLogEntity
import com.codelock.app.data.db.CategoryWithCount
import com.codelock.app.data.db.QuestionDao
import com.codelock.app.data.db.QuestionWithCategory
import com.codelock.app.data.db.StatsRow
import com.codelock.app.domain.AnswerResult
import com.codelock.app.domain.QuestionPicker
import com.codelock.app.domain.QuestionStats
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class QuestionRepository @Inject constructor(
    private val dao: QuestionDao,
) {
    fun observeCategories(): Flow<List<CategoryWithCount>> = dao.observeCategories()

    fun observeStats(): Flow<StatsRow> = dao.observeStats()

    suspend fun setCategoryEnabled(id: Long, enabled: Boolean) = dao.setCategoryEnabled(id, enabled)

    /** La siguiente pregunta de los temas activos, o null si no hay ninguna. */
    suspend fun nextQuestion(now: Long = System.currentTimeMillis()): QuestionWithCategory? {
        val candidates = dao.candidateStats().map {
            QuestionStats(it.questionId, it.timesAsked, it.timesWrong, it.lastAskedAt)
        }
        val recent = dao.recentQuestionIds(QuestionPicker.RECENT_WINDOW)
        val id = QuestionPicker.pick(candidates, recent, now) ?: return null
        return dao.questionWithCategory(id)
    }

    suspend fun recordAnswer(questionId: Long, result: AnswerResult, responseMs: Long) {
        dao.insertAnswer(
            AnswerLogEntity(
                questionId = questionId,
                result = result.code,
                responseMs = responseMs.coerceAtLeast(0),
                answeredAt = System.currentTimeMillis(),
            ),
        )
    }
}
