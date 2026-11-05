package com.codelock.app.data

import com.codelock.app.data.db.CategoryEntity
import com.codelock.app.data.db.QuestionDao
import com.codelock.app.data.db.QuestionEntity
import com.codelock.app.domain.SourceLinks
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class ImportResult(
    val categories: Int,
    val added: Int,
    val duplicates: Int,
    val errors: List<String>,
) {
    val summary: String
        get() = buildString {
            append("$added preguntas nuevas en $categories tema(s)")
            if (duplicates > 0) append(" · $duplicates ya existían")
            if (errors.isNotEmpty()) append(" · ${errors.size} con errores")
        }
}

/**
 * Importa preguntas desde JSON. Es el mismo formato del banco inicial, así que puedes
 * agregar temas nuevos (Historia, Inglés, Matemáticas...) sin tocar el código:
 *
 * {
 *   "version": 1,
 *   "categories": [
 *     { "name": "Kotlin",
 *       "questions": [
 *         { "statement": "¿...?", "options": ["a", "b", "c", "d"], "correct": 1,
 *           "explanation": "Porque...", "difficulty": 1,
 *           "source": "Documentación de Kotlin · Variables",
 *           "sourceUrl": "https://kotlinlang.org/docs/basic-syntax.html#variables" } ] } ]
 *
 * "source" y "sourceUrl" son opcionales: sin ellos, "Ver documentación" busca la pregunta en la web.
 * }
 */
@Singleton
class QuestionImporter @Inject constructor(
    private val dao: QuestionDao,
) {
    suspend fun importJson(json: String): ImportResult {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return ImportResult(0, 0, 0, listOf("El archivo no es un JSON válido: ${it.message}"))
        }
        val categories = root.optJSONArray("categories")
            ?: return ImportResult(0, 0, 0, listOf("Falta la lista \"categories\"."))

        var added = 0
        var duplicates = 0
        var touched = 0
        val errors = mutableListOf<String>()

        for (i in 0 until categories.length()) {
            val category = categories.optJSONObject(i) ?: continue
            val name = category.optString("name").trim()
            if (name.isEmpty()) {
                errors += "Tema ${i + 1}: falta el nombre."
                continue
            }
            val questionsJson = category.optJSONArray("questions") ?: continue
            val categoryId = dao.findCategory(name)?.id ?: dao.insertCategory(CategoryEntity(name = name))
            touched++

            val questions = buildList {
                for (j in 0 until questionsJson.length()) {
                    val q = questionsJson.optJSONObject(j) ?: continue
                    val statement = q.optString("statement").trim()
                    val optionsJson = q.optJSONArray("options")
                    val options = if (optionsJson == null) {
                        emptyList()
                    } else {
                        List(optionsJson.length()) { optionsJson.optString(it).trim() }.filter { it.isNotEmpty() }
                    }
                    val correct = q.optInt("correct", -1)
                    val problem = when {
                        statement.isEmpty() -> "falta el enunciado"
                        options.size !in 2..6 -> "debe tener entre 2 y 6 opciones"
                        correct !in options.indices -> "\"correct\" debe ser la posición (desde 0) de la opción correcta"
                        else -> null
                    }
                    if (problem != null) {
                        errors += "$name, pregunta ${j + 1}: $problem."
                        continue
                    }
                    add(
                        QuestionEntity(
                            categoryId = categoryId,
                            statement = statement,
                            options = options,
                            correctIndex = correct,
                            explanation = q.optString("explanation").trim().ifEmpty { null },
                            difficulty = q.optInt("difficulty", 1).coerceIn(1, 3),
                            source = q.optString("source").trim().ifEmpty { null },
                            sourceUrl = SourceLinks.cleanUrl(q.optString("sourceUrl")),
                        ),
                    )
                }
            }
            val ids = dao.insertQuestions(questions)
            added += ids.count { it != -1L }
            duplicates += ids.count { it == -1L }
            // Las que ya existían: si ahora traen fuente y antes no tenían, se la ponemos.
            questions.zip(ids).forEach { (q, id) ->
                if (id == -1L && q.source != null) dao.fillSource(categoryId, q.statement, q.source, q.sourceUrl)
            }
        }
        return ImportResult(touched, added, duplicates, errors)
    }
}
