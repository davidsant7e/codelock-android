package com.codelock.app.unlock

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Servicio en primer plano que detecta cuándo el usuario empieza a usar el celular.
 *
 * Escucha dos avisos del sistema:
 * - ACTION_USER_PRESENT: el usuario quitó la pantalla de bloqueo.
 * - ACTION_SCREEN_ON: se encendió la pantalla. Si en ese momento NO hay pantalla de bloqueo
 *   (sin bloqueo, Smart Lock o retraso de bloqueo), cuenta como desbloqueo, porque en ese
 *   caso Android no envía ACTION_USER_PRESENT.
 *
 * Desde Android 8 estos avisos no llegan a receptores declarados en el manifiesto,
 * por eso se registran aquí, en un servicio que se mantiene vivo con una notificación.
 */
@AndroidEntryPoint
class UnlockService : Service() {

    @Inject lateinit var launcher: QuestionLauncher

    private var registered = false
    private val main = Handler(Looper.getMainLooper())

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Aviso recibido: ${intent.action}")
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> launcher.onUnlock()
                Intent.ACTION_SCREEN_ON -> {
                    // Se espera un instante a que el sistema decida si muestra el bloqueo.
                    main.postDelayed({
                        val keyguard = getSystemService(KeyguardManager::class.java)
                        if (keyguard?.isKeyguardLocked == false) {
                            Log.i(TAG, "Pantalla encendida sin bloqueo: cuenta como desbloqueo")
                            launcher.onUnlock()
                        }
                    }, SCREEN_ON_CHECK_MS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.SERVICE_NOTIFICATION_ID,
                Notifications.serviceNotification(this),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }
        if (started.isFailure) {
            // Android puede negar el arranque desde segundo plano; se reintenta al abrir la app.
            Log.w(TAG, "No se pudo iniciar el servicio", started.exceptionOrNull())
            stopSelf()
            return
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // Son avisos protegidos: solo el sistema puede enviarlos, así que exportar es seguro
        // y evita que algunas versiones de Android los filtren.
        ContextCompat.registerReceiver(this, unlockReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
        Log.i(TAG, "Servicio iniciado: esperando desbloqueos")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (registered) unregisterReceiver(unlockReceiver)
        registered = false
        Log.i(TAG, "Servicio detenido")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CodeLock"
        private const val SCREEN_ON_CHECK_MS = 600L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, UnlockService::class.java))
            }.onFailure { Log.w(TAG, "No se pudo iniciar el servicio", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UnlockService::class.java))
        }
    }
}
