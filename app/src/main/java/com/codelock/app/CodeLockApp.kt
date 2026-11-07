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
     * Carga los bancos incluidos en la app (todos los .json de assets: questions_seed.json,
     * programacion-desde-cero.json...) en cada arranque. Las repetidas se ignoran, así que solo
     * agrega las preguntas nuevas de una actualización y completa las fuentes que falten.
     */
    private fun syncSeed() {
        appScope.launch {
            val banks = assets.list("").orEmpty().filter { it.endsWith(".json") }.sorted()
            for (bank in banks) {
                runCatching {
                    val json = assets.open(bank).bufferedReader().use { it.readText() }
                    importer.importJson(json)
                }.onSuccess { Log.i("CodeLock", "Banco $bank: ${it.summary}") }
                    .onFailure { Log.w("CodeLock", "No se pudo cargar $bank", it) }
            }
        }
    }
}
