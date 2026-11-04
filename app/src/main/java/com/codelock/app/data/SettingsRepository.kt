package com.codelock.app.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CodeLockSettings(
    val enabled: Boolean = false,
    /** Minutos mínimos entre una pregunta y la siguiente (0 = en cada desbloqueo). */
    val minIntervalMinutes: Int = 5,
    val lastShownAt: Long? = null,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<CodeLockSettings> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) = update { putBoolean(KEY_ENABLED, enabled) }

    fun setMinInterval(minutes: Int) = update { putInt(KEY_INTERVAL, minutes.coerceAtLeast(0)) }

    fun markShown(now: Long) = update { putLong(KEY_LAST_SHOWN, now) }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true) { block() }
        _state.value = read()
    }

    private fun read() = CodeLockSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        minIntervalMinutes = prefs.getInt(KEY_INTERVAL, 5),
        lastShownAt = prefs.getLong(KEY_LAST_SHOWN, 0L).takeIf { it > 0 },
    )

    companion object {
        const val PREFS = "codelock"
        const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "min_interval_minutes"
        private const val KEY_LAST_SHOWN = "last_shown_at"

        /** Lectura directa, usada al reiniciar el celular (antes de que exista Hilt). */
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
    }
}
