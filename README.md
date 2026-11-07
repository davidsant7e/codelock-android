# CodeLock — preguntas al desbloquear

<p align="center"><img src="docs/carta.svg" alt="V · El Hierofante" width="180"></p>


App Android que te hace una pregunta (programación u otros temas) cada vez que desbloqueas
el celular. Respondes, ves si acertaste y por qué, y sigues con lo tuyo.

## Qué hace

- **Detecta el desbloqueo** y abre una pregunta de opción múltiple.
- **Frecuencia configurable:** en cada desbloqueo, o como mínimo cada 5, 15, 30 o 60 minutos.
- **Temas activables:** viene con 32 preguntas propias en 6 temas (Programación general,
  Kotlin, Python, SQL, Git, HTTP y APIs). Puedes apagar los temas que no quieras.
- **Ampliable a cualquier tema** importando un archivo JSON (Historia, Inglés, Matemáticas…),
  sin tocar el código.
- **Elige preguntas con inteligencia:** prioriza las que nunca viste, repite más las que fallas,
  trae de vuelta las que no ves hace días y evita repetir las últimas 10.
- **Explicación** después de cada respuesta, y un botón "Otra pregunta".
- **Estadísticas:** respondidas y porcentaje de aciertos.
- Se vuelve a activar sola al reiniciar el celular.

## Cómo funciona por dentro

1. `UnlockService` es un servicio en primer plano (con una notificación discreta) que escucha
   `ACTION_USER_PRESENT`, el aviso que Android envía justo al desbloquear. Desde Android 8 ese
   aviso no llega a receptores del manifiesto; por eso se registra dentro del servicio.
2. `QuestionLauncher` revisa la frecuencia y abre `QuestionActivity`.
3. Android no deja que una app en segundo plano abra pantallas, salvo que tenga el permiso
   **"Mostrar sobre otras apps"**. Sin ese permiso, CodeLock avisa con una notificación
   ("Tienes una pregunta") que abre la pregunta al tocarla.
4. Las preguntas y tu historial se guardan en Room (SQLite): tablas `categories`,
   `questions` y `answer_log`.

## Abrir y probar

1. Android Studio → **Open** → carpeta `codelock-android`. Espera el Gradle Sync.
2. Conecta tu celular por USB (con depuración USB activada; ver el README de AudioNovel) y
   pulsa **Run ▶**. Esta app conviene probarla en un celular real: el emulador no simula bien
   el bloqueo y desbloqueo.
3. En la app:
   - Activa **Preguntar al desbloquear**.
   - Concede **Mostrar sobre otras apps** y **Notificaciones**.
   - Recomendado: **Batería sin restricciones**, para que el sistema no cierre CodeLock.
4. Bloquea y desbloquea el celular: debería aparecer la pregunta.

Mismas versiones que AudioNovel: AGP 9.4.0 con Kotlin integrado 2.4.10, Gradle 9.6.0,
Compose BOM 2026.09.00, Room 2.8.5 y Hilt 2.60.1.

### Si la pregunta no aparece al desbloquear

- Revisa que los permisos de la pantalla principal tengan la marca ✓.
- **Xiaomi / Redmi / POCO:** en Ajustes → Apps → CodeLock → Otros permisos, activa
  "Mostrar ventanas emergentes mientras se ejecuta en segundo plano" y "Inicio automático".
- **Samsung:** en Batería, quita CodeLock de "Apps en suspensión".
- **Huawei / Honor / OPPO / Vivo:** busca "Inicio automático" o "Administrar inicio" y permite CodeLock.
- Con el bloqueo de pantalla en "Ninguno", algunos celulares envían el aviso al encender
  la pantalla en lugar de al desbloquear.

## Importar preguntas (formato JSON)

Botón **Importar preguntas (JSON)** en la pantalla principal. Las preguntas repetidas
(mismo tema y mismo enunciado) se ignoran, así que puedes importar el mismo archivo
actualizado varias veces.

```json
{
  "version": 1,
  "categories": [
    {
      "name": "Historia",
      "questions": [
        {
          "statement": "¿En qué año llegó el ser humano a la Luna?",
          "options": ["1965", "1969", "1972", "1975"],
          "correct": 1,
          "explanation": "La misión Apolo 11 alunizó en julio de 1969.",
          "difficulty": 1,
          "source": "NASA · Apollo 11",
          "sourceUrl": "https://www.nasa.gov/mission/apollo-11/"
        }
      ]
    }
  ]
}
```

| Campo | Obligatorio | Detalle |
| --- | --- | --- |
| `name` | Sí | Nombre del tema. Si ya existe, las preguntas se agregan a ese tema. |
| `statement` | Sí | El enunciado. |
| `options` | Sí | Entre 2 y 6 opciones. |
| `correct` | Sí | Posición de la opción correcta, **contando desde 0** (la primera es 0). |
| `explanation` | No | Se muestra después de responder. |
| `difficulty` | No | 1 (fácil) a 3 (difícil). Por ahora se guarda para usarla más adelante. |
| `source` | No | De dónde sale la pregunta (nombre de la documentación). Se muestra en «De dónde sale». |
| `sourceUrl` | No | Enlace `https://` a esa documentación: botón **Ver documentación**. Sin enlace, el botón es **Buscar en la web** (busca la pregunta). |

Si importas otra vez un archivo con preguntas que ya tenías, a las que no tenían fuente se les agrega.

### «No sé» y «De dónde sale»

- **No sé**: debajo de las opciones. Muestra la respuesta y la explicación sin contarla como error en tu porcentaje; la pregunta vuelve pronto, igual que una fallada.
- **De dónde sale**: después de responder, la fuente de la pregunta y el botón para abrir la documentación en el navegador. Las 32 preguntas del banco inicial ya traen su fuente (documentación oficial de Kotlin, Python, PostgreSQL, el libro Pro Git, MDN o Wikipedia).

El banco inicial está en `app/src/main/assets/questions_seed.json` y usa este mismo formato;
sirve de ejemplo. Al abrir la app se cargan **todos** los `.json` de `assets/` (las repetidas se
ignoran), así que agregar un banco es soltar su archivo ahí.

### Banco «Programación desde cero»

`assets/programacion-desde-cero.json`: 120 preguntas del libro *Programación desde cero*
(5 por capítulo, 24 capítulos) en tres temas que puedes activar o desactivar por separado:
**Nivel 1 Fundamentos**, **Nivel 2 Desarrollo práctico** y **Nivel 3 Profundización**.
Cada pregunta dice de qué capítulo sale («Programación desde cero · Cap. 07 …») y, cuando
aplica, enlaza la documentación oficial de Python o Git. Es el mismo archivo que usa
CodeLock para Windows en `preguntas/`.

## Pruebas automáticas

```bash
./gradlew test
```

Prueban la frecuencia mínima y la elección de preguntas (que no se repitan las recientes y
que las falladas salgan más seguido).

## Estructura (MVVM, guía oficial de arquitectura)

```
app/src/main/java/com/codelock/app/
  domain/        reglas puras: frecuencia y elección de la siguiente pregunta
  data/
    db/          Room: temas, preguntas, historial de respuestas
    QuestionImporter.kt    importar JSON (también carga el banco inicial)
    QuestionRepository.kt  siguiente pregunta, guardar respuestas
    SettingsRepository.kt  activado, frecuencia, última pregunta
  unlock/        servicio de desbloqueo, lanzador, arranque, notificaciones
  ui/
    home/        pantalla principal: activar, permisos, frecuencia, temas, progreso
    question/    pantalla de la pregunta
  di/            módulos de Hilt
```

## Ideas para las siguientes versiones

- Generar preguntas nuevas con Claude a partir de un tema o de tus apuntes.
- Repetición espaciada más completa usando la dificultad.
- Preguntas de respuesta escrita o de completar código, además de opción múltiple.
- Sincronizar preguntas desde un servidor (WorkManager).
