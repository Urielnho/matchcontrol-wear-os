# MatchControl 1.0 — validación actual

Fecha: 19 de octubre de 2026. Esta versión utiliza exclusivamente resultados recibidos de servicios reales. La documentación de la versión anterior está archivada en `docs/historial/`.

## Compilación y pruebas

`assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**. **27 pruebas, cero fallos, errores u omisiones**. Reporte: [unit-results.json](artifacts/qa/v1-real/unit-results.json).

Se comprobaron la lógica de fútbol existente, filtros, fechas, límites de consultas, selección del protagonista, valores ausentes, mapeo de cuartos/entradas y clasificación de F1. Los fixtures de prueba son respuestas públicas capturadas de los servicios y no se incluyen en el APK.

Un caso real contiene un resultado de baloncesto de 100–111 con cuatro cuartos que suman 93–93 y sin prórroga publicada. La app conserva los valores recibidos y muestra **Desglose por confirmar**. La prueba correspondiente verifica que no se inventen puntos ni una prórroga para cuadrar el resultado.

## Pruebas en el reloj

AVD `MatchControl_Wear33`, Wear OS 4/API 33, cuadrado de 360 × 360 píxeles a 240 dpi. APK instalado mediante actualización, conservando los favoritos reales.

| Área | Comprobación y evidencia |
| --- | --- |
| Fútbol | [Inicio con resultados reales](artifacts/qa/v1-real/football-home.png), sin distintivos técnicos ni selector de simulación. |
| Ajustes | [Versión y opciones del usuario](artifacts/qa/v1-real/settings.png); conexión, avisos y competiciones. |
| Deportes | [Selector](artifacts/qa/v1-real/sport-picker.png) con fútbol, F1, baloncesto y béisbol. |
| F1 | [Calendario](artifacts/qa/v1-real/f1-calendar.png), [último resultado](artifacts/qa/v1-real/f1-podium.png), [clasificación](artifacts/qa/v1-real/f1-ranking.png) y [otro Gran Premio](artifacts/qa/v1-real/f1-historical-ranking.png), consultados al servicio. |
| Baloncesto | [Marcador de tres cifras](artifacts/qa/v1-real/basketball-detail.png), [cuartos](artifacts/qa/v1-real/basketball-quarters.png) y [aviso de desglose inconsistente](artifacts/qa/v1-real/basketball-breakdown-warning.png). No se afirma que el encuentro probado incluya una prórroga publicada. |
| Béisbol | [Resultado](artifacts/qa/v1-real/baseball-detail.png), [entradas](artifacts/qa/v1-real/baseball-innings.png), [hits y errores](artifacts/qa/v1-real/baseball-statistics.png). |
| Actualización manual | El contador local de fútbol pasó de 13 a 14 al pulsar Actualizar. En baloncesto pasó de 6 a 8: dos consultas UTC para cubrir el día local. Se respetaron los límites existentes. |

Los scripts y jerarquías XML de estas comprobaciones están en `artifacts/qa/v1-real/`, junto a `runtime-results.json` y `release-check.json`. La app quedó abierta en el inicio de fútbol con acceso al selector de deportes.

## Limpieza de la aplicación

- Eliminados el repositorio de demostración, los botones de simulación, el selector de modo y las etiquetas técnicas de origen/cuota.
- Migración de preferencias: se retiran equipos ficticios antiguos y se conservan los favoritos con IDs reales.
- Inspección del APK: no contiene DemoRepository, la acción «Simular» ni los JSON de pruebas. [Auditoría](artifacts/qa/v1-real/package-audit.json).
- Credencial sin coincidencias en fuentes, documentación actual, scripts ni capturas de esta versión. Configuración privada excluida del control de versiones.

## Alcance

Se probaron servicios reales y el emulador cuadrado. No se midieron FPS ni consumo en un reloj físico. La nueva versión multideporte no se declara validada en otros dispositivos. F1 muestra calendario y clasificaciones publicadas, sin telemetría en directo. Los periodos no publicados permanecen ausentes y se informa cuando el desglose no coincide con el total.
