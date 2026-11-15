package com.codelock.app.registro

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Arranca el [Registro] apenas se crea el proceso de la app (antes que cualquier pantalla o servicio),
 * sin tocar el resto del código. No comparte datos: todas sus consultas devuelven vacío.
 */
class RegistroInicio : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { Registro.iniciar(it, NOMBRE_APP) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val NOMBRE_APP = "V · El Hierofante (CodeLock)"
    }
}
