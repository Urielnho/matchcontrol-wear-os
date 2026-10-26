# MatchControl 1.0.1

Aplicación independiente para Wear OS con resultados reales de fútbol, baloncesto, béisbol y Fórmula 1. No incorpora encuentros ficticios ni controles de simulación. Conserva los favoritos de fútbol existentes.

## Abrir una copia del repositorio

Clona el proyecto y ábrelo en Android Studio. Copia `local.properties.example` como `local.properties`, configura tu propia `FOOTBALL_API_KEY` y deja que Android Studio registre la ruta del SDK. La configuración de ejecución `app` se incluye en `.run/`; espera a que termine la sincronización de Gradle antes de ejecutarla.

El repositorio excluye credenciales, APK, videos y la carpeta de entrega local. Para generar el APK utiliza `assembleDebug`. Las horas se muestran según la zona del reloj; para Phoenix utiliza `America/Phoenix` en sus ajustes de fecha y hora.

## Deportes

- **Fútbol:** marcadores, minuto, calendario, resultados, eventos y estadísticas cuando están publicados. Seguimiento de equipos y avisos de inicio, gol y final durante el uso de la app.
- **Baloncesto:** puntos, estado del encuentro, cuartos y prórroga cuando están publicados.
- **Béisbol:** carreras, entradas, hits y errores.
- **Fórmula 1:** próximo Gran Premio, calendario de la temporada y clasificaciones publicadas con pilotos, equipos, posiciones, tiempos, puntos y vueltas.

## Uso

1. Toca **Fútbol ⌄** en el encabezado para cambiar de deporte.
2. Abre un encuentro o Gran Premio para consultar su desglose.
3. En baloncesto y béisbol, cambia la fecha con las flechas que aparecen después del primer encuentro. Los resultados más recientes aparecen primero.
4. En fútbol, usa En vivo, Próximos, Resultados y Favoritos. Desde el detalle puedes seguir o dejar de seguir equipos.
5. Ajustes permite gestionar avisos y filtrar las competiciones de fútbol. Los favoritos y avisos actuales corresponden a fútbol.
6. **Actualizar** consulta de nuevo el servicio, respetando sus límites. La navegación reutiliza los resultados guardados. Sin conexión, solo se conservan datos reales previamente recibidos y se indica que están pendientes de actualizar.

Los valores ausentes se muestran como desconocidos. Si los periodos publicados no coinciden con el total, aparece «Desglose por confirmar»; la app no calcula puntuaciones faltantes. El paso de la hora programada no convierte una carrera en finalizada ni genera una clasificación. Los resultados y estadísticas dependen de lo que cada fuente publique.

## Fuentes

Fútbol, baloncesto y béisbol utilizan la cuenta configurada de [API-Sports](https://api-sports.io/). Se verificó el acceso con respuestas reales. Las consultas por fecha se adaptan a la zona horaria del reloj; para baloncesto y béisbol se consultan los dos días UTC que pueden intersectar el día local.

F1 utiliza [Jolpica](https://github.com/jolpica/jolpica-f1/blob/main/docs/README.md). Se comprobó que el plan actual de Formula-1 de API-Sports no permite consultar 2026, por lo que se usa esta fuente pública de resultados reales. Jolpica se consulta con el identificador de aplicación requerido y sin enviarle la credencial privada. Muestra calendario y clasificaciones publicadas; no ofrece telemetría en directo en esta integración.

No se muestran indicadores técnicos de origen ni cuotas dentro de la interfaz. Los límites y reservas se gestionan internamente por servicio.

## Ejecutar

Abre esta carpeta en Android Studio y utiliza el JDK integrado. Por PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. La versión de la aplicación es **1.0.1**, código 3. El emulador actual es `MatchControl_Round_QA`, circular de 454 × 454 a 320 dpi. También se conserva el perfil cuadrado `MatchControl_Wear33`.

La configuración privada permanece en `local.properties`, excluida del control de versiones. La arquitectura actual es un cliente directo: antes de distribuir públicamente, la credencial debe custodiarse en un backend y configurarse la firma de distribución. Esta entrega instala la aplicación local; no publica en una tienda.

## Comprobaciones

[Validación de fechas, resultados y límites del plan](docs/VALIDACION-DATOS.md). Las horas se convierten a la zona del reloj; el emulador circular se ajustó a America/Phoenix.

[Validación actual](VALIDACION.md), [diseño y arquitectura](REDISENO.md) y [capturas de la versión 1.0](artifacts/qa/v1-real/).

Los documentos de la versión anterior se conservan únicamente como registro en `docs/historial/`.

## Preparar la presentación

Ejecuta `powershell -ExecutionPolicy Bypass -File .\ejecutar-circular.ps1` para compilar, iniciar el reloj circular e instalar la app. Usa `-SinCompilar` si el APK ya está actualizado.

La [guía de presentación](docs/GUIA-PRESENTACION.md) contiene el recorrido, la explicación del código y los límites de la aplicación. La carpeta `entrega/` reúne el APK, código sin configuración privada, guía, capturas y video.

La versión 1.0.1 incorpora iniciales de equipos, accesos por filas independientes para desplazamiento circular, fecha de actualización en inicio, reintento visible al fallar y permisos de avisos coherentes con la elección del usuario. Activar avisos produce una confirmación; tocar un aviso deportivo abre el partido correspondiente. Las observaciones anteriores se conservan entre aperturas para comparar cambios reales, sin enviar alertas en la primera observación ni por puntuaciones desconocidas.
