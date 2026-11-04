package com.codelock.app.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codelock.app.R
import com.codelock.app.data.db.CategoryWithCount
import com.codelock.app.ui.question.QuestionActivity
import com.codelock.app.ui.theme.AnswerColors

private val INTERVALS = listOf(0 to "Siempre", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hora")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Al volver de los ajustes del sistema, se revisa de nuevo qué permisos hay.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("Entendido") } },
            title = { Text("Importar preguntas") },
            text = { Text(message) },
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("CodeLock") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ActivationCard(
                    enabled = state.settings.enabled,
                    overlayGranted = state.permissions.overlay,
                    activeQuestions = state.activeQuestions,
                    onToggle = viewModel::setEnabled,
                )
            }
            item {
                PermissionsCard(
                    permissions = state.permissions,
                    onOverlay = { context.openOverlaySettings() },
                    onNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.openNotificationSettings()
                        }
                    },
                    onBattery = { context.openBatterySettings() },
                )
            }
            item {
                FrequencyCard(selected = state.settings.minIntervalMinutes, onSelect = viewModel::setInterval)
            }
            item {
                SectionTitle("Temas")
            }
            items(state.categories, key = { it.id }) { category ->
                CategoryRow(category, onToggle = { viewModel.setCategoryEnabled(category.id, it) })
            }
            item {
                StatsCard(answered = state.answered, accuracy = state.accuracy)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { context.startActivity(QuestionActivity.intent(context)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Probar una pregunta ahora") }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Importar preguntas (JSON)") }
                    Text(
                        "Agrega temas nuevos con un archivo JSON. El formato está en el README del proyecto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivationCard(enabled: Boolean, overlayGranted: Boolean, activeQuestions: Int, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Preguntar al desbloquear", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                val status = when {
                    !enabled -> "Desactivado"
                    activeQuestions == 0 -> "Activo, pero no hay temas con preguntas activados"
                    !overlayGranted -> "Activo · llegará como notificación (falta un permiso)"
                    else -> "Activo · $activeQuestions preguntas disponibles"
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionsState,
    onOverlay: () -> Unit,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Permisos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                title = "Mostrar sobre otras apps",
                description = "Necesario para abrir la pregunta justo al desbloquear.",
                granted = permissions.overlay,
                onGrant = onOverlay,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            PermissionRow(
                title = "Notificaciones",
                description = "Para el aviso de CodeLock activo y el respaldo por notificación.",
                granted = permissions.notifications,
                onGrant = onNotifications,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            PermissionRow(
                title = "Batería sin restricciones",
                description = "Recomendado: evita que el sistema cierre CodeLock. En la lista, elige CodeLock → Sin restricciones.",
                granted = permissions.batteryUnrestricted,
                onGrant = onBattery,
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = "Concedido",
                tint = AnswerColors.correct,
            )
        } else {
            TextButton(onClick = onGrant) { Text("Conceder") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrequencyCard(selected: Int, onSelect: (Int) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Frecuencia", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tiempo mínimo entre una pregunta y la siguiente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                INTERVALS.forEach { (minutes, label) ->
                    FilterChip(
                        selected = selected == minutes,
                        onClick = { onSelect(minutes) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun CategoryRow(category: CategoryWithCount, onToggle: (Boolean) -> Unit) {
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${category.questionCount} preguntas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = category.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun StatsCard(answered: Int, accuracy: Int) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Tu progreso", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (answered == 0) "Todavía no has respondido preguntas." else "$answered respondidas · $accuracy % de aciertos",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ------------------------------------------------------- ajustes del sistema
private fun Context.openOverlaySettings() {
    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
}

private fun Context.openNotificationSettings() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}

private fun Context.openBatterySettings() {
    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}
