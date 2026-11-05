package com.codelock.app.ui.question

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codelock.app.data.QuestionRepository
import com.codelock.app.data.db.QuestionWithCategory
import com.codelock.app.di.ApplicationScope
import com.codelock.app.domain.AnswerResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuestionUiState(
    val loading: Boolean = true,
    val question: QuestionWithCategory? = null,
    val selected: Int? = null,
    /** Pulsó "No sé": se muestra la respuesta sin marcarla como error. */
    val dontKnow: Boolean = false,
) {
    val empty: Boolean get() = !loading && question == null
    val answered: Boolean get() = selected != null || dontKnow
    val correct: Boolean get() = question != null && selected == question.question.correctIndex
}

@HiltViewModel
class QuestionViewModel @Inject constructor(
    private val repository: QuestionRepository,
    // Las respuestas se guardan en el alcance de la app: no se pierden si la pantalla se cierra.
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(QuestionUiState())
    val state: StateFlow<QuestionUiState> = _state.asStateFlow()

    private var shownAt = 0L

    init {
        loadNext()
    }

    fun loadNext() {
        _state.value = QuestionUiState(loading = true)
        viewModelScope.launch {
            val question = repository.nextQuestion()
            shownAt = System.currentTimeMillis()
            _state.value = QuestionUiState(loading = false, question = question)
        }
    }

    fun answer(index: Int) {
        val current = _state.value
        val question = current.question ?: return
        if (current.answered) return
        _state.value = current.copy(selected = index)
        val result = if (index == question.question.correctIndex) AnswerResult.CORRECT else AnswerResult.WRONG
        record(question, result)
    }

    /** "No sé": muestra la respuesta y la explicación; la pregunta vuelve pronto. */
    fun dontKnow() {
        val current = _state.value
        val question = current.question ?: return
        if (current.answered) return
        _state.value = current.copy(dontKnow = true)
        record(question, AnswerResult.DONT_KNOW)
    }

    /** Cerrar sin responder cuenta como "saltada" (no como error). */
    fun skipIfUnanswered() {
        val current = _state.value
        val question = current.question ?: return
        if (!current.answered) record(question, AnswerResult.SKIPPED)
    }

    private fun record(question: QuestionWithCategory, result: AnswerResult) {
        val elapsed = System.currentTimeMillis() - shownAt
        appScope.launch { repository.recordAnswer(question.question.id, result, elapsed) }
    }
}
