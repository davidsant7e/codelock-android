package com.codelock.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

// ------------------------------------------------------------------ tablas

/** Un tema: Programación, Kotlin, Historia... Agregar temas no requiere tocar código. */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index(value = ["categoryId", "statement"], unique = true)],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val statement: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String?,
    val difficulty: Int = 1,
    /** De dónde sale la pregunta: nombre de la documentación (p. ej. "Documentación de Kotlin · Data classes"). */
    val source: String? = null,
    /** Enlace a esa documentación (http/https). */
    val sourceUrl: String? = null,
)

/** Historial: cada vez que respondes (o saltas) una pregunta. */
@Entity(
    tableName = "answer_log",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("questionId"), Index("answeredAt")],
)
data class AnswerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    /** 1 = correcta, 0 = incorrecta, 2 = "no sé", -1 = saltada (ver AnswerResult). */
    val result: Int,
    val responseMs: Long,
    val answeredAt: Long,
)

// ------------------------------------------------------- filas de consultas

data class CategoryWithCount(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val questionCount: Int,
)

data class QuestionStatsRow(
    val questionId: Long,
    val timesAsked: Int,
    val timesWrong: Int,
    val lastAskedAt: Long?,
)

data class QuestionWithCategory(
    @Embedded val question: QuestionEntity,
    @ColumnInfo(name = "categoryName") val categoryName: String,
)

data class StatsRow(
    val answered: Int,
    val correct: Int,
)

// --------------------------------------------------------------------- DAO

@Dao
interface QuestionDao {

    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.enabled AS enabled, COUNT(q.id) AS questionCount
        FROM categories c LEFT JOIN questions q ON q.categoryId = c.id
        GROUP BY c.id ORDER BY c.name COLLATE NOCASE
        """,
    )
    fun observeCategories(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findCategory(name: String): CategoryEntity?

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("UPDATE categories SET enabled = :enabled WHERE id = :id")
    suspend fun setCategoryEnabled(id: Long, enabled: Boolean)

    /** Las preguntas repetidas (mismo tema y enunciado) se ignoran: devuelven -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestions(questions: List<QuestionEntity>): List<Long>

    /** Completa la fuente de una pregunta que ya existía sin ella (bancos de versiones anteriores). */
    @Query(
        """
        UPDATE questions SET source = :source, sourceUrl = :sourceUrl
        WHERE categoryId = :categoryId AND statement = :statement AND (source IS NULL OR source = '')
        """,
    )
    suspend fun fillSource(categoryId: Long, statement: String, source: String, sourceUrl: String?)

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun questionCount(): Int

    @Query(
        """
        SELECT q.id AS questionId,
               COUNT(l.id) AS timesAsked,
               COALESCE(SUM(CASE WHEN l.result IN (0, 2) THEN 1 ELSE 0 END), 0) AS timesWrong,
               MAX(l.answeredAt) AS lastAskedAt
        FROM questions q
        JOIN categories c ON c.id = q.categoryId
        LEFT JOIN answer_log l ON l.questionId = q.id
        WHERE c.enabled = 1
        GROUP BY q.id
        """,
    )
    suspend fun candidateStats(): List<QuestionStatsRow>

    @Query("SELECT questionId FROM answer_log ORDER BY answeredAt DESC LIMIT :limit")
    suspend fun recentQuestionIds(limit: Int): List<Long>

    @Query(
        """
        SELECT q.*, c.name AS categoryName
        FROM questions q JOIN categories c ON c.id = q.categoryId
        WHERE q.id = :id
        """,
    )
    suspend fun questionWithCategory(id: Long): QuestionWithCategory?

    @Insert
    suspend fun insertAnswer(answer: AnswerLogEntity)

    @Query(
        """
        SELECT COUNT(*) AS answered,
               COALESCE(SUM(CASE WHEN result = 1 THEN 1 ELSE 0 END), 0) AS correct
        FROM answer_log WHERE result IN (0, 1)
        """,
    )
    fun observeStats(): Flow<StatsRow>
}

// --------------------------------------------------------- base de datos

class Converters {
    @TypeConverter
    fun optionsToJson(options: List<String>): String = JSONArray(options).toString()

    @TypeConverter
    fun jsonToOptions(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }
}

@Database(
    entities = [CategoryEntity::class, QuestionEntity::class, AnswerLogEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    companion object {
        /** v2: fuente y enlace de cada pregunta. Conserva el historial. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN source TEXT")
                db.execSQL("ALTER TABLE questions ADD COLUMN sourceUrl TEXT")
            }
        }
    }
}
