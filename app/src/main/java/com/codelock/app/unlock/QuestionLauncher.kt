package com.codelock.app.unlock

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.codelock.app.data.SettingsRepository
import com.codelock.app.domain.FrequencyPolicy
import com.codelock.app.ui.question.QuestionActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decide, al desbloquear, si toca una pregunta y la abre.
 *
 * Desde Android 15, el permiso "Mostrar sobre otras apps" solo permite abrir una pantalla
 * desde segundo plano si la app tiene una ventana flotante visible en ese momento. Por eso
 * se pone un "ancla" flotante de 1×1 píxel (transparente e intocable) justo antes de abrir
 * la pregunta y se quita a los dos segundos.
 *
 * Si aun así Android no deja abrirla (no lanza error, la descarta en silencio), se detecta
 * porque [QuestionActivity] no avisa que abrió, y se muestra la notificación de respaldo.
 */
@Singleton
class QuestionLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val main = Handler(Looper.getMainLooper())

    private var lastUnlockAt = 0L

    fun onUnlock() {
        // Encender la pantalla y quitar el bloqueo pueden llegar casi juntos: cuentan como uno.
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastUnlockAt < DEBOUNCE_MS) return
        lastUnlockAt = elapsed

        val current = settings.state.value
        val now = System.currentTimeMillis()
        Log.i(TAG, "Desbloqueo detectado (activo=${current.enabled}, intervalo=${current.minIntervalMinutes} min)")
        if (!FrequencyPolicy.shouldAsk(current.enabled, current.lastShownAt, now, current.minIntervalMinutes)) {
            Log.i(TAG, "Todavía no toca pregunta")
            return
        }
        settings.markShown(now)

        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Sin permiso 'Mostrar sobre otras apps': se usa la notificación")
            Notifications.showQuestionPrompt(context)
            return
        }

        val requestedAt = SystemClock.elapsedRealtime()
        openWithOverlayAnchor()
        main.postDelayed({
            if (lastOpenedAt < requestedAt) {
                Log.w(TAG, "Android no abrió la pregunta: se muestra la notificación de respaldo")
                Notifications.showQuestionPrompt(context)
            }
        }, CHECK_DELAY_MS)
    }

    private fun openWithOverlayAnchor() {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val anchor = View(context)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        val anchored = runCatching { windowManager.addView(anchor, params) }
            .onFailure { Log.w(TAG, "No se pudo poner la ventana ancla", it) }
            .isSuccess

        main.postDelayed({
            runCatching { context.startActivity(QuestionActivity.intent(context)) }
                .onSuccess { Log.i(TAG, "Pregunta solicitada (ancla=$anchored)") }
                .onFailure { Log.w(TAG, "No se pudo abrir la pregunta", it) }
            main.postDelayed({
                if (anchored) runCatching { windowManager.removeView(anchor) }
            }, REMOVE_ANCHOR_MS)
        }, OPEN_DELAY_MS)
    }

    companion object {
        private const val TAG = "CodeLock"
        private const val OPEN_DELAY_MS = 150L
        private const val REMOVE_ANCHOR_MS = 2_000L
        private const val CHECK_DELAY_MS = 2_500L
        private const val DEBOUNCE_MS = 5_000L

        /** Momento (reloj de arranque) en que se abrió la pantalla de la pregunta por última vez. */
        @Volatile
        var lastOpenedAt: Long = 0L
            private set

        /** Lo llama [QuestionActivity] al abrirse. */
        fun markOpened() {
            lastOpenedAt = SystemClock.elapsedRealtime()
        }
    }
}
