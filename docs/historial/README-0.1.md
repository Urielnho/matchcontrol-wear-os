# MatchControl · Wear OS

Aplicación nativa e independiente para relojes Wear OS, en Kotlin y Compose for Wear OS Material 3. No requiere teléfono, cuenta ni servicios adicionales. En una instalación nueva arranca en **DEMOSTRACIÓN**, con equipos ficticios claramente identificados. El modo elegido persiste localmente.

## Emulador preparado en esta computadora

Ya se creó **MatchControl_Wear33** (Wear OS 4, API 33), actualmente configurado como **Wear OS Square**, por preferencia del usuario y se instaló MatchControl. Puedes seleccionarlo en Device Manager de Android Studio. En este equipo ya está configurada la clave privada y la aplicación quedó abierta en **modo API real**, con partidos recibidos del proveedor. La interfaz adapta sus márgenes y recorte según la forma de la pantalla. El tema deportivo comparte tarjetas redondeadas; los márgenes y el recorte se adaptan a cada geometría. Resolución 360 × 360 con escala de 240 dpi (~240 dp). Las comprobaciones circulares anteriores se conservaron en el registro de validación. No necesita teléfono. El modo demostración también funciona sin API Key.

## Abrir y ejecutar

1. En Android Studio selecciona **Open** y abre esta carpeta (la que contiene `settings.gradle.kts`).
2. En Settings > Build, Execution, Deployment > Build Tools > Gradle, selecciona **Embedded JDK / jbr (Java 21)**. Deja que termine **Sync Project with Gradle Files**. Se requiere Internet para la primera descarga.
3. En Tools > Device Manager > Create Virtual Device, selecciona la categoría **Wear OS**, un reloj **circular** y una imagen Wear OS con API 33 o superior. Descárgala y termina el asistente. Una imagen Android de teléfono no sustituye la imagen Wear OS.
4. Inicia ese emulador, selecciona la configuración `app` y pulsa Run. La app se instala directamente en el reloj virtual.
5. Navega por En vivo, Próximos, Resultados, Favoritos y Configuración. Desliza desde el borde izquierdo o pulsa Volver. Desplázate verticalmente para ver todas las opciones.

Por terminal PowerShell, si Java del sistema no es compatible:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug testDebugUnitTest
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Probar funciones

- Todos los listados permiten cambiar de fecha y actualizar. El filtro de ligas elegido en Configuración se aplica a los listados.
- Abre un partido y toca el nombre del equipo en **Seguir equipos**. La marca de verificación indica que lo sigues. Puedes quitarlo desde ese control o desde su tarjeta en Favoritos. La selección persiste al cerrar y abrir la aplicación.
- En Configuración activa los avisos y concede el permiso de notificaciones de Android.
- En los detalles de un partido demo marca al equipo local como favorito y pulsa **Simular Inicio**, **Simular Gol** o **Simular Final**. Cambian el estado y marcador y se genera una notificación local identificada como DEMO. El mismo estado de evento no se notifica dos veces. Los partidos simulados se reinician al reiniciar el proceso; favoritos y ajustes se conservan.
- Desactiva notificaciones para comprobar que no se envían. Si negaste el permiso, habilítalo en Settings > Apps > MatchControl > Notifications del emulador.
- Sin API Key, al cambiar a modo real se muestra un error explicativo; no se sustituyen datos reales por datos ficticios. Vuelve a demostración para continuar.

## API-Football integrada

La clave ya se guardó únicamente en `local.properties`, excluido de Git. La conexión se validó con `/status`, `/fixtures`, `/fixtures?id=` y `/leagues`. La cuenta respondió con plan Free activo y límite de 100 solicitudes diarias. No se escribe la credencial en esta documentación ni en registros.

Para reemplazar la clave local, conserva `sdk.dir` y cambia esta propiedad privada, luego recompila:

```properties
FOOTBALL_API_KEY=tu_clave_real
```

La clave se envía en `x-apisports-key` sobre HTTPS. El APK de este prototipo local contiene la configuración compilada y permite extraer la clave; antes de distribuir, utiliza un backend que custodie la credencial y centralice la cuota.

En Configuración puedes alternar entre **API real** y **DEMO**. Al cambiar se limpian los partidos y las marcas de actualización del modo anterior. Un error de API conserva únicamente su propia caché; no se sustituye por información simulada.

Los IDs de las ocho competiciones se verificaron con la respuesta real de `/leagues` y se guardaron en `app/src/main/assets/competitions.json`:

| Competición | ID verificado |
| --- | --- |
| Liga MX | 262 |
| UEFA Champions League | 2 |
| Premier League de Inglaterra | 39 |
| LaLiga | 140 |
| Serie A de Italia | 135 |
| Bundesliga de Alemania | 78 |
| Ligue 1 de Francia | 61 |
| Mundial | 1 |

Los filtros reales usan el ID, evitando confundir competiciones de distintos países con nombres iguales. Las preferencias antiguas por nombre se migran automáticamente. En demostración se admite la correspondencia de nombres del catálogo. Sin filtros seleccionados se muestran todos los partidos devueltos para la fecha.

Las consultas por fecha envían también la zona horaria del reloj (`timezone`). Las fechas con desplazamiento del proveedor se interpretan con `OffsetDateTime` y se muestran en hora local. La caché distingue fecha y zona horaria.

Se validó la fecha 19/10/2026 y el detalle de un partido real. El catálogo de temporadas no garantiza acceso a todas ellas: otras fechas, competiciones y estadísticas dependen de la cobertura y del plan. La interfaz muestra solo eventos y valores disponibles. Los encuentros cancelados o abandonados no aparecen como próximos; los resultados adjudicados se clasifican como finalizados.

## Arquitectura y dependencias

- `domain`: modelos y contrato `FootballRepository`.
- `data`: repositorios demo/API, cliente, caché, DataStore y notificaciones.
- `presentation`: ViewModel, StateFlow, pantallas y navegación Wear con gesto de regreso.
- `MainActivity`: entrada y permiso de notificaciones.

MVVM con dependencias creadas en el ViewModel, sin contenedor DI adicional. DataStore guarda ajustes y favoritos; la caché de respuestas JSON se guarda en almacenamiento privado. Room no es necesario para este volumen. Componentes Wear Material 3, TransformingLazyColumn, Card, Button, AppScaffold y ScreenScaffold con márgenes para pantalla circular. Coil carga escudos cuando hay URL.

Versiones fijadas: AGP 8.13.0, Gradle 8.13, Kotlin y plugin Compose 2.2.10, Wear Compose 1.5.0, Compose BOM 2025.08.00, Lifecycle 2.9.2, DataStore 1.1.7, Retrofit 2.11.0 y OkHttp 4.12.0. SDK 36; mínimo API 30.

## Consumo y notificaciones

Se refresca al abrir la aplicación y cada 15 minutos con encuentros en vivo, o cada 60 minutos sin ellos, únicamente en primer plano. Las peticiones de la misma clave se serializan y usan caché de 15 minutos (24 horas para detalles finalizados y metadatos de ligas). El botón Actualizar respeta también esa caché. Se reservan hasta 95 llamadas por día UTC antes de acceder a la red, incluyendo intentos fallidos. Además del contador local se conserva la última cabecera `x-ratelimit-requests-remaining` del proveedor. No se realizan nuevas consultas cuando quedan cinco o menos solicitudes remotas. La cabecera permite considerar consumos de otros clientes hasta la última respuesta recibida; no es un contador global instantáneo.

Si falla la red, el proveedor rechaza la consulta o se agota el presupuesto, se conserva la última respuesta válida y se identifica como posiblemente desactualizada. Si no existe caché se muestra el error. Al cambiar de modo no se mezclan los datos.

Las notificaciones reales comparan dos consultas de la sesión para detectar inicio, aumento de goles de un equipo favorito y final. No se notifica el histórico inicial. No hay sondeo en segundo plano, servicio permanente ni garantía de avisos instantáneos. Para avisos con la app cerrada haría falta una arquitectura adicional de backend/push, pendiente de definir con el plan del proveedor.

## Estado por fases

1. Estructura Gradle, arquitectura y navegación: implementadas.
2. Pantallas Wear con demostración: implementadas.
3. Favoritos, ajustes, filtros, caché, estados y fechas: implementados.
4. API: conectada y verificada con la clave local; partidos por fecha, detalles, cuota remota e IDs de competiciones validados. Pendiente ampliar la verificación de cobertura e historial según el plan.
5. Notificaciones locales y simulación: implementadas; seguimiento real limitado al primer plano.
6. Verificación: compilación del APK y 17 pruebas unitarias completadas correctamente. Consulta `VALIDACION.md` para los resultados. Ya se abrió y observó la interfaz en un emulador Wear OS circular. Falta completar las pruebas de todas las pantallas, persistencia y notificaciones; consulta el detalle de validación.

Fuentes oficiales consultadas: [Wear Material 3](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3), [API-Football v3](https://www.api-football.com/documentation-v3), [optimización de cuota](https://www.api-football.com/news/post/how-to-optimize-api-sports-calls-and-quota-usage).

## Interfaz deportiva

El rediseño y sus referencias se documentan en [REDISENO.md](REDISENO.md). Home destaca un partido, los accesos usan iconos circulares, Próximos agrupa horarios, y el detalle presenta cronología y barras comparativas. Los eventos y valores ausentes permanecen ausentes; DEMO se identifica en todas las pantallas.

- `presentation/theme/MatchTheme.kt`: paleta, tipografía y espaciado comunes.
- `presentation/components/`: marcador animado, MatchCard, escudos, iconos vectoriales y estados visuales.
- `presentation/home/`: selección del protagonista y navegación de inicio.
- `presentation/SportScreens.kt`: composiciones específicas de cada sección.
- `presentation/MatchApp.kt`: scaffolds Wear, gesto de regreso, listas transformables, indicador de desplazamiento y acción de borde.

Los equipos vistos se guardan en un catálogo local acotado que retiene los favoritos. Las animaciones son breves y respetan la preferencia de movimiento reducido. No se añadió una dependencia de Material para teléfonos.
