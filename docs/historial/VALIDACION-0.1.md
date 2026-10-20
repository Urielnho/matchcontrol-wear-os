# Verificación de MatchControl

Entorno inspeccionado inicialmente: Windows, Android Studio y su JBR 21, SDK Android 36, adb disponible. No había proyecto previo, emulador iniciado ni imagen Wear OS instalada. Las imágenes existentes son de teléfono y televisión.

## Comprobaciones

- `gradlew.bat --version`: ejecutado correctamente, Gradle 8.13 con JBR 21.
- Gradle 8.13 recuperado después de una interrupción de red; integridad ZIP comprobada.
- `gradlew.bat assembleDebug testDebugUnitTest --console=plain`: **BUILD SUCCESSFUL**, 18 de octubre de 2026.
- **8 pruebas ejecutadas, 0 fallos y 0 errores**, confirmado en los reportes XML de Gradle.
- APK generado: `app/build/outputs/apk/debug/app-debug.apk`.
- Se corrigió una anotación experimental inexistente en Wear Material 3 estable y se migró la configuración de Kotlin a `compilerOptions`.
- Ocho pruebas unitarias incluidas (también cubren consistencia de la simulación, reinicio y equipos entre fechas): estados en vivo/finalizados, fecha local alrededor de medianoche, marcadores ausentes, estadísticas parciales y respuesta sin identidad de partido.
- No se realizó ninguna llamada autenticada a API-Football: no hay credencial.
- Aplicación instalada y ejecutada el 18 de octubre de 2026 en **MatchControl_Wear33**, Wear OS 4 / API 33, x86_64, pantalla circular de 454 × 454 y densidad 320.
- Imagen oficial descargada de Google y verificada con SHA-1 `250b8682cc0a4430c8963f2927a097296856fe22` antes de extraerla.
- `adb install -r`: **Success**. `am start -W`: **Status: ok**, actividad `com.matchcontrol/.MainActivity`.
- Confirmada la característica de sistema `watch`; no se utilizó una imagen de teléfono.
- Interfaz observada en Inicio y En vivo, con etiqueta DEMOSTRACIÓN, fecha y dos partidos en vivo. Se verificó el regreso a Inicio.
- Búfer de cierres de Android consultado después de abrir y navegar: sin entradas de error.
- Captura: `artifacts/qa/inicio-wearos.png`. La app queda abierta en el emulador.

## Revisión pendiente para completar la validación

1. Primer inicio sin teléfono y apertura de MatchControl: comprobados. Repetir una prueba tras reiniciar el emulador.
2. Revisar todos los textos y tarjetas en un reloj pequeño y uno grande; comprobar que se puede llegar al último botón y volver mediante gesto.
3. Recorrer las siete pantallas, cambiar fechas y comprobar estados vacíos al elegir ligas sin partidos demo.
4. Seguir y quitar equipos; cerrar y abrir la aplicación para verificar persistencia.
5. Activar avisos, conceder permiso y simular inicio, gol y final. Comprobar marcador y una notificación por evento; repetir Final para verificar deduplicación.
6. Denegar el permiso y desactivar avisos; confirmar que no se publican notificaciones.
7. Cambiar a modo API sin clave: debe mostrar error y no resultados demo. Regresar a demostración.
8. Con clave real y cobertura validada: cargar un día, cortar Internet y comprobar que se conserva la respuesta guardada y se informa el estado; comprobar también un día sin caché.
9. Validar nombres e IDs de ligas, temporadas, cobertura de eventos/estadísticas y consumo en el dashboard del proveedor.

La existencia de código y pruebas no sustituye las verificaciones pendientes indicadas arriba.


## Adaptación circular — 19 de octubre de 2026

- Se compactaron cabeceras y fechas; el modo DEMO continúa identificado en todas las pantallas.
- Márgenes laterales proporcionales al ancho circular, recorte del contenido a la pantalla y espacio inferior para llevar la última acción al centro.
- Botones y tarjetas usan `transformedHeight` y `SurfaceTransformation` de Wear Material 3 para reducir y desvanecer los elementos próximos al borde.
- Tarjetas con mayor redondeo; paleta negra y verde discreto.
- `assembleDebug`: BUILD SUCCESSFUL. APK actualizado e instalado correctamente.
- Verificación en el emulador circular de 454 × 454: densidad normal 320 (~227 dp) y densidad de prueba 380 (~191 dp). Se comprobaron Inicio, En vivo y navegación a detalles en el tamaño pequeño.
- En ambos tamaños se observaron completos los nombres de los dos equipos, los dos marcadores y el minuto del primer partido. Se guardaron capturas y árboles de accesibilidad en `artifacts/qa/circular-*`.
- Sin entradas en el búfer de cierres de Android después de la comprobación.
- Se restauró la densidad 320 y la aplicación quedó abierta en En vivo.
- Pendientes las pruebas completas de las restantes pantallas, tamaños de fuente ampliados y los casos de integración real ya indicados arriba.

Referencias de implementación: [SurfaceTransformation](https://developer.android.com/reference/kotlin/androidx/wear/compose/material3/SurfaceTransformation) y [TransformingLazyColumn](https://developer.android.com/reference/kotlin/androidx/wear/compose/foundation/lazy/TransformingLazyColumn).


## Integración API real — 19 de octubre de 2026

- Clave guardada únicamente en configuración privada `local.properties`, excluida de Git. No se incluye en documentación ni código fuente versionable.
- Conexión HTTPS autenticada verificada contra `/status`: suscripción Free activa, límite de 100 solicitudes diarias.
- Consultas reales verificadas: `/fixtures?date=2026-10-19&timezone=America/Phoenix`, `/fixtures?id=…` y `/leagues`.
- Se recibieron y almacenaron en caché 200 partidos reales para la fecha consultada; esta cifra corresponde a esa respuesta de prueba, no es una constante de la aplicación.
- Catálogo de las ocho competiciones verificado con el proveedor y guardado en `app/src/main/assets/competitions.json`. Los filtros reales utilizan ID, no nombres ambiguos. Preferencias previas por nombre migradas a IDs.
- Se lee y conserva la cuota restante enviada por el proveedor. Presupuesto local máximo de 95 solicitudes diarias y reserva remota de cinco.
- Compilación final `assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL. Reportes XML: **17 pruebas, 0 fallos, 0 errores**.
- En el emulador se activó API real desde Configuración, se observaron partidos reales en En vivo y se abrió el detalle de un partido. La etiqueta de origen mostró API.
- Persistencia y caché comprobadas al cerrar y reabrir: el modo real se conservó y el contador de red permaneció en una solicitud antes de consultar detalles.
- Detalle real recibido y guardado en caché; el encuentro probado devolvió cero eventos y cero bloques de estadísticas. No se completaron con datos ficticios.
- Detectada y corregida la interpretación de fechas con `+00:00` en Android: se usa `OffsetDateTime`. Verificado en pantalla `19/10 · 01:00` para el inicio `2026-10-19T08:00:00+00:00` en Phoenix.
- La aplicación queda abierta en En vivo en modo API real. Capturas: `artifacts/qa/api-live.png`, `api-settings.png` y `api-details.png`.
- Pendiente ampliar pruebas de otras fechas y cobertura por competición, fallos de red reales y seguimiento prolongado de notificaciones. No se habilita sondeo en segundo plano ni se garantiza una notificación instantánea.


## Cambio a reloj cuadrado — 19 de octubre de 2026

- A petición del usuario, el AVD existente `MatchControl_Wear33` se cambió al perfil oficial `wearos_square`, pantalla 360 × 360, circularidad desactivada y escala del sistema ajustada a 240 dpi (~240 dp).
- Nombre visible: MatchControl Wear OS cuadrado. Se conservó el almacenamiento del emulador, la aplicación, favoritos, caché y modo API real.
- Se guardó una copia del perfil circular original como `config.circular.backup.ini` dentro de la carpeta del AVD.
- La aplicación utiliza recorte rectangular, margen inferior de 16 dp y tarjetas con esquinas de 16 dp en pantalla cuadrada. El diseño circular sigue disponible según la configuración del dispositivo.
- Compilación `assembleDebug`: BUILD SUCCESSFUL. APK actualizado instalado: Success.
- Inicio y En vivo revisados en el emulador; ambos equipos, marcador y minuto del primer partido visibles. Etiqueta API confirmada.
- Capturas: `artifacts/qa/square-home.png` y `square-live.png`.
- La aplicación queda abierta en En vivo en el reloj cuadrado.


## Rediseño deportivo — 19 de octubre de 2026

- Se mantuvo el proyecto Kotlin, sus repositorios, API, preferencias y favoritos. La auditoría, referencias y decisiones están en `REDISENO.md`.
- Tema negro/lima/rojo central, Home con partido protagonista, accesos circulares, MatchCard reutilizable, horarios específicos de Próximos, cronología y barras con datos disponibles, catálogo persistente de equipos y estados con iconos.
- Wear Material 3 1.5.0: AppScaffold, ScreenScaffold, TransformingLazyColumn, TimeText, indicador de desplazamiento, EdgeButton y regreso nativo. Sin Material de teléfonos.
- BUILD SUCCESSFUL en `assembleDebug testDebugUnitTest`: **22 pruebas, 0 fallos, 0 errores, 0 omitidas**. Resumen en `artifacts/qa/premium-unit-results.json`.
- Revisión de las siete secciones en el AVD circular de 454 px a 320/380 dpi (~227/191 dp). Dos tamaños lógicos sobre el mismo perfil, no dos dispositivos físicos. Capturas `premium-round-*`.
- Marcador DEMO actualizado de 1:0 a 2:0; cronología y comparativas observadas. Acción de borde alcanzable y etiqueta corregida para ocupar una línea.
- Favorito agregado, conservado al reiniciar y al cambiar de fecha, y eliminado desde su tarjeta.
- API sin conexión y sin caché: error visible, sin sustitución por DEMO. Recuperación de DEMO y gesto de regreso verificados.
- En el AVD cuadrado original se mantuvieron modo API, caché y favorito. Se comprobó la caché real desactualizada al desconectar y la recepción de datos al reconectar.
- El estado de conexión ahora sigue callbacks del sistema y libera el observador al destruir el ViewModel; la recuperación del aviso se probó sin reiniciar.
- Capturas finales con renderizado por software del emulador; la aplicación sigue usando Compose con aceleración normal. No se midieron FPS, batería ni un reloj físico.
- La cobertura de eventos y estadísticas reales depende del partido y del proveedor; los valores ausentes no se inventan. Las barras se validan con ambos valores numéricos disponibles.

- APK final instalado con `Success`. Siete destinos revisados en el cuadrado; no se observaron cierres y el buffer de crashes quedó vacío en la comprobación final. La app quedó en **Home, modo API**, con el emulador cuadrado abierto; el AVD circular de pruebas se cerró.
- Auditoría final de la credencial: sin coincidencias en fuentes, documentación, scripts ni capturas XML/JSON. No se añadieron imports de Material 3 para teléfonos.
- Evidencia de las vistas cuadradas: `premium-square-home.png`, `premium-square-live.png`, `premium-square-upcoming.png`, `premium-square-results.png`, `premium-square-favorites.png`, `premium-square-settings.png` y `premium-square-api-details.png`.
