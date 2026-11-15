package com.codelock.app.registro

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Registro de la app: una línea por evento, un archivo por día, 14 días. Mismo formato en todas las apps
 * de los Arcanos, pensado para que lo lea una persona o Claude (claves en español, sin JSON):
 *
 *   2026-11-15 14:40:12.345 | APP | inicio | version=0.2.0 | celular=motorola edge 50 fusion | android=16 (API 36)
 *   2026-11-15 14:41:03.002 | GEMINI | error | tipo=IOException | mensaje=Se acabó el cupo | donde=Gemini.kt:88
 *   2026-11-15 14:41:09.871 | CIERRE | inesperado | tipo=IllegalStateException | mensaje=…   (y debajo la pila)
 *
 * Dónde queda: /sdcard/Android/data/<paquete>/files/registros/registro-AAAA-MM-DD.txt
 * Desde el PC:  adb pull /sdcard/Android/data/<paquete>/files/registros
 * Desde el celular: mantener presionado el ícono de la app → «Enviar registro».
 *
 * Nunca guarda claves, contraseñas ni tokens (se reemplazan por ***) y corta los textos largos.
 * Lo inicia solo [RegistroInicio] al abrir la app (también desde servicios), así que nunca falta.
 */
object Registro {
    const val DIAS = 14
    private const val MAX_DIA_BYTES = 2_000_000L
    private const val MAX_VALOR = 300

    private val hilo = Executors.newSingleThreadExecutor { r -> Thread(r, "registro").apply { isDaemon = true } }
    private val formatoHora = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }
    private val formatoDia = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    private val secretas = Regex("clave|key|token|pass|contrase|secret|pat\\b|authorization", RegexOption.IGNORE_CASE)

    @Volatile private var carpeta: File? = null
    @Volatile var nombreApp: String = "app"
        private set

    /** Se llama una vez al arrancar el proceso. Instala el aviso de cierres inesperados. */
    @Synchronized
    fun iniciar(context: Context, nombre: String) {
        if (carpeta != null) return
        val c = context.applicationContext ?: context
        nombreApp = nombre
        carpeta = (runCatching { c.getExternalFilesDir("registros") }.getOrNull() ?: File(c.filesDir, "registros")).apply { mkdirs() }
        hilo.execute { limpiarViejos() }

        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hiloFallo, e ->
            runCatching { cierreInesperado(hiloFallo, e) }
            anterior?.uncaughtException(hiloFallo, e)
        }

        val info = runCatching { c.packageManager.getPackageInfo(c.packageName, 0) }.getOrNull()
        evento(
            "APP", "inicio",
            "version" to info?.versionName,
            "celular" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "proceso" to Process.myPid(),
        )
    }

    /** Una acción o algo que pasó: `Registro.evento("RUTA", "calculada", "km" to 12.3, "paradas" to 4)`. */
    fun evento(accion: String, fase: String, vararg datos: Pair<String, Any?>) {
        escribir(linea(System.currentTimeMillis(), accion, fase, datos.toList()))
    }

    /** Un error atrapado (la app siguió funcionando). */
    fun error(accion: String, e: Throwable?, vararg datos: Pair<String, Any?>) {
        val extra = if (e == null) emptyList() else listOf(
            "tipo" to e.javaClass.simpleName,
            "mensaje" to (e.message ?: e.cause?.message),
            "donde" to dondeFallo(e),
        )
        escribir(linea(System.currentTimeMillis(), accion, "error", datos.toList() + extra))
    }

    /** Arma una línea. Público para probarlo sin Android. */
    fun linea(momento: Long, accion: String, fase: String, datos: List<Pair<String, Any?>>): String {
        val sb = StringBuilder()
        sb.append(formatoHora.get()!!.format(Date(momento))).append(" | ").append(limpiar(accion)).append(" | ").append(limpiar(fase))
        for ((k, v) in datos) {
            if (v == null) continue
            val texto = when {
                secretas.containsMatchIn(k) -> "***"
                v is Double -> numero(v)
                v is Float -> numero(v.toDouble())
                v is Collection<*> -> v.joinToString(" ")
                else -> v.toString()
            }
            if (texto.isEmpty()) continue
            val corto = if (texto.length > MAX_VALOR) texto.take(MAX_VALOR) + "…(${texto.length})" else texto
            sb.append(" | ").append(limpiar(k)).append('=').append(limpiar(corto))
        }
        return sb.toString()
    }

    /** Una línea no puede tener saltos ni el separador « | ». */
    fun limpiar(s: String): String = s.replace("\r", " ").replace("\n", " ⏎ ").replace(" | ", " / ")

    fun numero(v: Double): String =
        if (v.isNaN() || v.isInfinite()) "?" else (Math.round(v * 100) / 100.0).toString().replace('.', ',')

    /** Primera línea de la pila que es de la app (archivo:línea), para saber dónde pasó. */
    fun dondeFallo(e: Throwable): String? {
        val paquete = Registro::class.java.name.substringBeforeLast(".registro")
        val pila = generateSequence(e) { it.cause }.flatMap { it.stackTrace.asSequence() }
        val propio = pila.firstOrNull { it.className.startsWith(paquete) && !it.className.contains(".registro.") }
            ?: e.stackTrace.firstOrNull()
        return propio?.let { "${it.fileName}:${it.lineNumber} (${it.methodName})" }
    }

    private fun cierreInesperado(hiloFallo: Thread, e: Throwable) {
        val ahora = System.currentTimeMillis()
        val pila = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
            .lines().take(60).joinToString("\n") { "    $it" }
        val texto = buildString {
            append(linea(ahora, "CIERRE", "inesperado", listOf(
                "hilo" to hiloFallo.name, "tipo" to e.javaClass.name, "mensaje" to e.message, "donde" to dondeFallo(e),
            ))).append('\n')
            append(pila).append('\n')
            val log = logcat(150)
            if (log.isNotBlank()) append("    --- logcat de la app antes del cierre ---\n").append(log.lines().joinToString("\n") { "    $it" }).append('\n')
        }
        // Síncrono: el proceso está por morir.
        runCatching { archivoDe(ahora)?.appendText(texto) }
    }

    private fun escribir(linea: String) {
        val ahora = System.currentTimeMillis()
        hilo.execute {
            runCatching {
                val f = archivoDe(ahora) ?: return@runCatching
                if (f.length() < MAX_DIA_BYTES) f.appendText(linea + "\n")
            }
        }
    }

    /** Últimas líneas del log de Android de esta app (Android solo deja leer las propias). */
    fun logcat(lineas: Int): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "time", "-t", lineas.toString()).redirectErrorStream(true).start()
        val salida = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        salida.trim()
    }.getOrDefault("")

    /** Texto para compartir: el registro de los últimos días (lo más reciente) y el logcat de la app. */
    fun textoParaEnviar(maxCaracteres: Int = 60_000): String {
        val cabecera = "Registro de $nombreApp\n" +
            "Celular: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Archivos: ${carpeta?.absolutePath ?: "(sin carpeta)"}\n\n"
        val log = logcat(200)
        val pie = if (log.isBlank()) "" else "\n--- logcat de la app (últimas líneas) ---\n" + log.takeLast(12_000)
        val espacio = (maxCaracteres - cabecera.length - pie.length).coerceAtLeast(5_000)
        val lineas = ArrayList<String>()
        var total = 0
        loop@ for (f in archivos()) { // del más reciente al más viejo
            val l = runCatching { f.readLines() }.getOrDefault(emptyList())
            for (i in l.indices.reversed()) {
                if (total + l[i].length + 1 > espacio) break@loop
                lineas.add(l[i]); total += l[i].length + 1
            }
        }
        lineas.reverse()
        return cabecera + lineas.joinToString("\n") + pie
    }

    fun archivos(): List<File> =
        carpeta?.listFiles { f -> f.name.startsWith("registro-") && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.name } ?: emptyList()

    fun ruta(): String = carpeta?.absolutePath ?: "(sin carpeta)"

    private fun archivoDe(momento: Long): File? {
        val dir = carpeta ?: return null
        return File(dir, "registro-" + formatoDia.get()!!.format(Date(momento)) + ".txt")
    }

    private fun limpiarViejos() {
        val limite = System.currentTimeMillis() - DIAS * 86_400_000L
        archivos().filter { it.lastModified() < limite }.forEach { it.delete() }
    }
}
