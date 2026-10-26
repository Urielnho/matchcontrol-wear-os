# MatchControl 1.0 — diseño y arquitectura

La interfaz utiliza el tema deportivo negro, blanco, lima y rojo de `presentation/theme`. Sigue empleando componentes Wear Material 3: AppScaffold, ScreenScaffold, TransformingLazyColumn, indicador de desplazamiento, TimeText, controles táctiles y navegación con gesto.

El encabezado permite cambiar de deporte. Los listados priorizan el primer encuentro y colocan el selector de fecha después de su marcador. Los escudos y abreviaturas permiten compactar tarjetas; el detalle conserva los nombres completos. El estado, los puntos y los tiempos proceden de los servicios de resultados.

## Separación de datos

- `ApiRepository` conserva el flujo de fútbol, su caché, cuota, filtros y detalles.
- `SportsRepository` incorpora baloncesto y béisbol mediante sus servicios específicos, y F1 mediante Jolpica. Se validan las respuestas antes de almacenarlas y se separa la caché por deporte y consulta.
- `SportsMapper` mantiene modelos propios para puntos/cuarto, carreras/entrada y pilotos/clasificación. No reutiliza el modelo de partido de fútbol para representar una carrera.
- `SportsViewModel` mantiene la navegación, fecha, encuentros y detalle de los deportes nuevos, independiente de los favoritos de fútbol.
- `MultiSportScreens` contiene tarjetas y detalles específicos para cada modalidad.

La selección de otra fecha cancela las cargas anteriores y filtra según el día local. Los resultados ausentes no se convierten en cero. Se detectan diferencias entre el total y los periodos publicados y se avisa sin inventar una prórroga ni rellenar entradas. Un Gran Premio sin resultados publicados queda pendiente, aunque su fecha ya haya pasado. Una hora no publicada permanece desconocida.

## Interfaz final

Se eliminaron el repositorio de demostración, el selector de modo, las acciones de simulación y los distintivos técnicos. La migración elimina los equipos ficticios antiguos, preservando los favoritos con IDs reales. Ajustes conserva únicamente opciones útiles para el usuario.

La actualización manual fuerza una consulta nueva; el uso normal aprovecha caché y límites para no agotar solicitudes. La conectividad se observa sin sondeo adicional. Las animaciones de pulsación y marcador respetan movimiento reducido.

## Referencias

- [Guías de Wear OS](https://developer.android.com/design/ui/wear).
- [Material 3 Expressive para Wear OS](https://android-developers.googleblog.com/2025/08/introducing-material-3-expressive-for-wear-os.html?debug=true).
- [Documentación Basketball](https://api-sports.io/documentation/basketball/v1) y [Baseball](https://api-sports.io/documentation/baseball/v1).
- [Jolpica: calendario, resultados y requisitos de consulta](https://github.com/jolpica/jolpica-f1/blob/main/docs/README.md).

La auditoría del diseño inicial y sus referencias adicionales se conservan en `docs/historial/REDISENO-0.1.md`. La evidencia actual está en [VALIDACION.md](VALIDACION.md).
