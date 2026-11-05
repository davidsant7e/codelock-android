package com.codelock.app.ui.question

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codelock.app.data.db.QuestionWithCategory
import com.codelock.app.domain.SourceLinks
import com.codelock.app.ui.theme.AnswerColors

private val LETTERS = listOf("A", "B", "C", "D", "E", "F")

@Composable
fun QuestionScreen(onClose: () -> Unit, viewModel: QuestionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val close = {
        viewModel.skipIfUnanswered()
        onClose()
    }
    BackHandler(onBack = close)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.empty -> EmptyState(onClose)
                else -> state.question?.let { question ->
                    QuestionContent(
                        question = question,
                        selected = state.selected,
                        dontKnow = state.dontKnow,
                        correct = state.correct,
                        onAnswer = viewModel::answer,
                        onDontKnow = viewModel::dontKnow,
                        onSkip = close,
                        onAnother = viewModel::loadNext,
                        onDone = onClose,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(
    question: QuestionWithCategory,
    selected: Int?,
    dontKnow: Boolean,
    correct: Boolean,
    onAnswer: (Int) -> Unit,
    onDontKnow: () -> Unit,
    onSkip: () -> Unit,
    onAnother: () -> Unit,
    onDone: () -> Unit,
) {
    val answered = selected != null || dontKnow
    val q = question.question
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(question.categoryName) })
            Spacer(Modifier.weight(1f))
            if (!answered) TextButton(onClick = onSkip) { Text("Saltar") }
        }

        Text(
            q.statement,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        q.options.forEachIndexed { index, option ->
            OptionCard(
                letter = LETTERS.getOrElse(index) { "${index + 1}" },
                text = option,
                state = when {
                    !answered -> OptionState.IDLE
                    index == q.correctIndex -> OptionState.CORRECT
                    index == selected -> OptionState.WRONG
                    else -> OptionState.DIMMED
                },
                enabled = !answered,
                onClick = { onAnswer(index) },
            )
        }

        if (!answered) {
            TextButton(onClick = onDontKnow, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("No sé")
            }
        }

        if (answered) {
            Feedback(
                correct = correct,
                dontKnow = dontKnow,
                correctLetter = LETTERS.getOrElse(q.correctIndex) { "${q.correctIndex + 1}" },
                explanation = q.explanation,
            )
            SourceCard(question)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAnother, modifier = Modifier.weight(1f)) { Text("Otra pregunta") }
                Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Listo") }
            }
        }
    }
}

private enum class OptionState { IDLE, CORRECT, WRONG, DIMMED }

@Composable
private fun OptionCard(letter: String, text: String, state: OptionState, enabled: Boolean, onClick: () -> Unit) {
    val accent = when (state) {
        OptionState.CORRECT -> AnswerColors.correct
        OptionState.WRONG -> AnswerColors.wrong
        else -> null
    }
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(if (accent != null) 2.dp else 1.dp, accent ?: MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = accent?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surface,
            disabledContainerColor = accent?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                letter,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (state == OptionState.DIMMED) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun Feedback(correct: Boolean, dontKnow: Boolean, correctLetter: String, explanation: String?) {
    val color = when {
        dontKnow -> MaterialTheme.colorScheme.primary
        correct -> AnswerColors.correct
        else -> AnswerColors.wrong
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when {
                    dontKnow -> "No pasa nada: la respuesta es la $correctLetter"
                    correct -> "¡Correcto!"
                    else -> "Incorrecto"
                },
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold,
            )
            if (!explanation.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "De dónde sale": la fuente de la pregunta y un botón para abrir la documentación. */
@Composable
private fun SourceCard(question: QuestionWithCategory) {
    val q = question.question
    val uriHandler = LocalUriHandler.current
    val hasUrl = SourceLinks.isWebUrl(q.sourceUrl)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("De dónde sale", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                q.source ?: "Esta pregunta no tiene fuente guardada (tema: ${question.categoryName}).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                // Sin navegador instalado, openUri falla: no cerramos la pantalla por eso.
                runCatching { uriHandler.openUri(SourceLinks.linkFor(q.statement, question.categoryName, q.sourceUrl)) }
            }) {
                Text(if (hasUrl) "Ver documentación" else "Buscar en la web")
            }
        }
    }
}

@Composable
private fun EmptyState(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No hay preguntas disponibles", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Activa al menos un tema con preguntas en CodeLock, o importa un archivo de preguntas.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose) { Text("Cerrar") }
    }
}
