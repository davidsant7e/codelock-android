package com.codelock.app.ui.question

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.codelock.app.ui.theme.CodeLockTheme
import com.codelock.app.unlock.Notifications
import com.codelock.app.unlock.QuestionLauncher
import dagger.hilt.android.AndroidEntryPoint

/** Pantalla que aparece al desbloquear (o al tocar la notificación de pregunta). */
@AndroidEntryPoint
class QuestionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QuestionLauncher.markOpened()
        enableEdgeToEdge()
        Notifications.cancelQuestionPrompt(this)
        setContent {
            CodeLockTheme {
                QuestionScreen(onClose = { finish() })
            }
        }
    }

    /** Si la pantalla ya estaba abierta (singleTask), también cuenta como abierta. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        QuestionLauncher.markOpened()
        Notifications.cancelQuestionPrompt(this)
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, QuestionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
