package com.codelock.app

import android.app.Application
import android.util.Log
import com.codelock.app.data.QuestionImporter
import com.codelock.app.di.ApplicationScope
import com.codelock.app.unlock.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class CodeLockApp : Application() {

    @Inject lateinit var importer: QuestionImporter

    @Inject
    @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        syncSeed()
    }

    /**
     * Carga el banco inicial (assets/questions_seed.json) en cada arranque: las repetidas se ignoran,
     * así que solo agrega las preguntas nuevas de una actualización y completa las fuentes que falten.
     */
    private fun syncSeed() {
        appScope.launch {
            val json = assets.open("questions_seed.json").bufferedReader().use { it.readText() }
            val result = importer.importJson(json)
            Log.i("CodeLock", "Banco inicial: ${result.summary}")
        }
    }
}
