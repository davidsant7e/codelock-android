package com.codelock.app.unlock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codelock.app.R
import com.codelock.app.ui.home.MainActivity
import com.codelock.app.ui.question.QuestionActivity

object Notifications {
    const val CHANNEL_SERVICE = "servicio"
    const val CHANNEL_QUESTION = "preguntas"
    const val SERVICE_NOTIFICATION_ID = 1
    private const val QUESTION_NOTIFICATION_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "CodeLock activo", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Aviso permanente mientras CodeLock espera el desbloqueo."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_QUESTION, "Preguntas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisa que hay una pregunta cuando no se puede abrir directamente."
            },
        )
    }

    fun serviceNotification(context: Context): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("CodeLock está activo")
            .setContentText("Te hará una pregunta al desbloquear el celular.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp)
            .build()
    }

    /** Respaldo si no se puede abrir la pregunta directamente (falta "Mostrar sobre otras apps"). */
    @SuppressLint("MissingPermission")
    fun showQuestionPrompt(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            1,
            QuestionActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_QUESTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tienes una pregunta")
            .setContentText("Toca para responder y seguir aprendiendo.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(QUESTION_NOTIFICATION_ID, notification)
    }

    fun cancelQuestionPrompt(context: Context) {
        NotificationManagerCompat.from(context).cancel(QUESTION_NOTIFICATION_ID)
    }
}
