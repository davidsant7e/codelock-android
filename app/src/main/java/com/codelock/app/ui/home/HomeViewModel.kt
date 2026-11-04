package com.codelock.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codelock.app.data.CodeLockSettings
import com.codelock.app.data.QuestionImporter
import com.codelock.app.data.QuestionRepository
import com.codelock.app.data.SettingsRepository
import com.codelock.app.data.db.CategoryWithCount
import com.codelock.app.unlock.UnlockService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionsState(
    /** "Mostrar sobre otras apps": necesario para abrir la pregunta al desbloquear. */
    val overlay: Boolean = false,
    val notifications: Boolean = true,
    /** Sin restricciones de batería: evita que el sistema cierre CodeLock. */
    val batteryUnrestricted: Boolean = false,
)

data class HomeUiState(
    val settings: CodeLockSettings = CodeLockSettings(),
    val permissions: PermissionsState = PermissionsState(),
    val categories: List<CategoryWithCount> = emptyList(),
    val answered: Int = 0,
    val correct: Int = 0,
    val message: String? = null,
) {
    val accuracy: Int get() = if (answered == 0) 0 else correct * 100 / answered
    val activeQuestions: Int get() = categories.filter { it.enabled }.sumOf { it.questionCount }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val repository: QuestionRepository,
    private val importer: QuestionImporter,
) : ViewModel() {

    private val permissions = MutableStateFlow(readPermissions())
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(
        settings.state,
        permissions,
        repository.observeCategories(),
        repository.observeStats(),
        message,
    ) { s, p, categories, stats, msg ->
        HomeUiState(s, p, categories, stats.answered, stats.correct, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(settings = settings.state.value))

    init {
        // Si estaba activo (por ejemplo, el sistema cerró el servicio), se vuelve a encender.
        if (settings.state.value.enabled) UnlockService.start(context)
    }

    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    fun setEnabled(enabled: Boolean) {
        settings.setEnabled(enabled)
        if (enabled) UnlockService.start(context) else UnlockService.stop(context)
    }

    fun setInterval(minutes: Int) = settings.setMinInterval(minutes)

    fun setCategoryEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setCategoryEnabled(id, enabled) }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (json == null) {
                message.value = "No se pudo leer el archivo."
                return@launch
            }
            val result = importer.importJson(json)
            message.value = buildString {
                append(result.summary)
                result.errors.take(3).forEach { append("\n• ").append(it) }
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun readPermissions(): PermissionsState {
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        val power = context.getSystemService(PowerManager::class.java)
        return PermissionsState(
            overlay = Settings.canDrawOverlays(context),
            notifications = notifications,
            batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
        )
    }
}
