package com.codelock.app.registro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistroTest {
    @Test fun `linea legible y sin secretos`() {
        val l = Registro.linea(0L, "GEMINI", "consulta", listOf(
            "modelo" to "gemini-3.5-flash", "clave" to "AIzaSECRETA", "apiKey" to "x", "km" to 12.345, "vacio" to null, "texto" to "a | b\nc",
        ))
        val partes = l.split(" | ")
        assertEquals("GEMINI", partes[1]); assertEquals("consulta", partes[2])
        assertTrue(l.contains("modelo=gemini-3.5-flash"))
        assertTrue(l.contains("clave=***")); assertTrue(l.contains("apiKey=***"))
        assertTrue(!l.contains("AIza"))
        assertTrue(l.contains("km=12,35"))
        assertTrue(!l.contains("vacio"))
        assertTrue(l.contains("texto=a / b ⏎ c"))
    }

    @Test fun `corta valores largos`() {
        val l = Registro.linea(0L, "X", "y", listOf("t" to "a".repeat(1000)))
        assertTrue(l.contains("…(1000)")); assertTrue(l.length < 400)
    }

    @Test fun `donde fallo apunta a la app`() {
        val e = RuntimeException("x")
        assertTrue(Registro.dondeFallo(e)!!.contains("RegistroTest.kt"))
    }
}
