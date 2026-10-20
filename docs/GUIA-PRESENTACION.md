# Presentar MatchControl

## Preparación

Abre el proyecto en Android Studio, espera la sincronización, inicia **MatchControl_Round_QA** desde Tools → Device Manager y ejecuta **app** con Shift + F10. También puedes abrir PowerShell en la carpeta del proyecto y ejecutar:

```powershell
powershell -ExecutionPolicy Bypass -File .\ejecutar-circular.ps1
```

Si ya compilaste, añade `-SinCompilar`. En otra computadora, instala Android Studio y los SDK que solicite Gradle; crea un dispositivo Wear OS circular con Android 13/API 33 o posterior. El script permite indicar su nombre con `-Avd`. Configura tu propia clave en `local.properties` mediante `FOOTBALL_API_KEY=tu_clave`; ese archivo privado no se entrega con el código. Nunca muestres la clave durante la exposición.

## Recorrido de cinco minutos

1. **Problema y propuesta:** consultar resultados deportivos desde la muñeca con pocas interacciones. Enseña la hora, el partido destacado y las iniciales de cada equipo.
2. **Navegación:** desliza hacia arriba, entra en En vivo, Próximos o Resultados. Cambia la fecha y abre un partido. Un estado vacío también es un resultado real; no prometas que siempre habrá encuentros en juego.
3. **Personalización:** sigue un equipo desde el detalle, vuelve a Favoritos y explica que la selección se guarda en el reloj incluso al cerrar la aplicación.
4. **Datos y conexión:** señala la fecha de actualización. Explica que Actualizar pide información nueva y que sin conexión se pueden consultar respuestas previamente guardadas. Si no se consultó esa fecha antes, no habrá datos guardados.
5. **Avisos:** activa los avisos desde Ajustes y concede el permiso. Aparece una notificación auténtica de confirmación. Los avisos deportivos requieren equipos seguidos y cambios publicados entre actualizaciones; no se inventan goles para la exposición. Tocar un aviso deportivo abre ese partido.
6. **Otros deportes:** toca Fútbol ⌄. Enseña puntos y cuartos en baloncesto, carreras y entradas en béisbol o posiciones de pilotos en Fórmula 1, según los datos publicados.

## Cómo explicar el código

- **Compose para Wear OS:** dibuja las pantallas y adapta el contenido desplazable al reloj circular. Los controles tienen áreas de toque de al menos 48 dp.
- **ViewModel y StateFlow:** mantienen el estado de la pantalla y separan la carga de datos de los componentes visuales.
- **Repositorios:** reciben respuestas reales, las convierten en modelos por deporte y conservan respuestas locales para usarlas sin conexión.
- **DataStore:** conserva equipos y competiciones elegidas, además de la preferencia de avisos.
- **Notificaciones:** comparan estados publicados del mismo partido y evitan repetir avisos. Solo se comparan resultados conocidos; una puntuación desconocida no se convierte en cero.
- **Pruebas:** verifican fechas, transformación de resultados, valores ausentes, límites de consultas y cambios que deben producir avisos.

## Límites que debes explicar con honestidad

Los avisos se comprueban al actualizar la aplicación mientras se usa; no hay un servicio de alertas permanentes en segundo plano. La frecuencia de actualización respeta el plan disponible. F1 muestra calendario y clasificaciones publicadas, sin telemetría en directo. Los favoritos corresponden al fútbol. Las pruebas de interfaz se realizaron en emuladores; no se afirma una medición de batería en un reloj físico.

## Entrega

La carpeta `entrega/` contiene el APK para instalación local, el código sin configuración privada, esta guía, capturas y un video del recorrido. El APK se firma con la clave de desarrollo; no es una publicación en una tienda. Lleva también el proyecto abierto y el emulador preparado.
