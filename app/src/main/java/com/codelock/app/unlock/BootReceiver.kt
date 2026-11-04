package com.codelock.app.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codelock.app.data.SettingsRepository

/** Al reiniciar el celular (o actualizar la app), vuelve a activar CodeLock si estaba activo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (relevant && SettingsRepository.isEnabled(context)) {
            UnlockService.start(context)
        }
    }
}
