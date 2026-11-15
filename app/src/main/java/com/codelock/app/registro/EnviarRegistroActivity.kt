package com.codelock.app.registro

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * «Enviar registro»: se abre con el atajo del ícono (mantener presionado el ícono de la app).
 * Comparte el registro como texto, por ejemplo con la app de Claude, WhatsApp o el correo. No tiene pantalla propia.
 */
class EnviarRegistroActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Registro.evento("REGISTRO", "enviar")
        val texto = Registro.textoParaEnviar()
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Registro de ${Registro.nombreApp}")
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        try {
            startActivity(Intent.createChooser(enviar, "Enviar registro de ${Registro.nombreApp}"))
        } catch (e: Exception) {
            Toast.makeText(this, "No hay una app para compartir texto.", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
