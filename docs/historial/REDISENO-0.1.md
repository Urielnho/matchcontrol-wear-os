# MatchControl — propuesta y auditoría de rediseño

## Auditoría inicial

Siete pantallas funcionales, MVVM/StateFlow, Wear Material 3 1.5.0, navegación con gesto, API real y demo, caché, cuota, ajustes y favoritos. El problema principal es una misma composición textual para propósitos distintos: Home es un menú, los marcadores son secundarios, eventos/estadísticas están aplanados en cadenas y no existen estados visuales específicos. Los estilos están concentrados en una pantalla monolítica.

## Referencias consultadas antes de implementar

- [Material 3 Expressive Wear](https://android-developers.googleblog.com/2025/08/introducing-material-3-expressive-for-wear-os.html?debug=true): superficies y tipografía expresivas, formas que responden al toque, listas adaptadas y acciones de borde. El enlace sin parámetro falló inicialmente; la variante del mismo artículo pudo consultarse.
- [Guías Wear](https://developer.android.com/design/ui/wear) y [calidad adaptable](https://developer.android.com/design/ui/wear/guides/foundations/quality-tiers): consultas de segundos y márgenes proporcionales. [Layouts comunes](https://developer.android.com/design/ui/wear/guides/foundations/common-layouts): distinguir composiciones de consulta rápida y listas desplazables.
- [Samples oficiales](https://github.com/android/wear-os-samples): ComposeStarter como referencia de separación de entrada/tema y componentes Wear. Se consultó el repositorio; la apertura web de su subcarpeta falló, por lo que no se afirma haber ejecutado ni auditado todo el sample.
- [Horologist](https://github.com/google/horologist): separación de composables, layout y estado. No se agregará esta dependencia: el proyecto ya dispone de los componentes Wear que necesita. La subcarpeta de layout no se pudo abrir con el navegador; sí se revisó su documentación general.
- [Wrist Goal](https://www.wristgoal.com/): jerarquía centrada en equipos, partido y estadísticas; es una referencia conceptual para Huawei, no una implementación Wear OS para copiar. Su sitio indica que las imágenes son ilustrativas.
- [Sofascore oficial](https://corporate.sofascore.com/) y [ficha oficial de su app](https://play.google.com/store/apps/details?id=com.sofascore.results): organizar la información deportiva para una lectura inmediata; no se afirma haber instalado ni reproducido su interfaz Wear.

## Propuesta concreta

1. Tema central: negro #000000, superficies #151515/#222222, blanco #FFFFFF, texto secundario #AAAAAA, lima #B8F35A solo para énfasis, rojo #FF5252 para LIVE. Escala tipográfica con números tabulares, espaciados y formas comunes.
2. Home: identidad discreta y ajustes, partido protagonista seleccionado priorizando favorito en vivo, después otro en vivo y próximo partido. Marcador central, escudos y abreviaturas; cuatro accesos circulares con iconos, adaptados en dos filas en pantallas pequeñas.
3. Listados: marcador central de lectura rápida, estados distintivos, fecha compacta y controles táctiles claros. Conservan filtros y actualización.
4. Detalles: panel de marcador, controles de seguimiento, cronología de eventos tipados y comparativas con valores reales. Los datos ausentes se presentan como no disponibles, nunca se rellenan con estadísticas inventadas.
5. Favoritos: equipos guardados con escudos/nombres y acciones de quitar; sus partidos debajo. Se conserva la persistencia actual y se amplía el catálogo local de equipos vistos para mostrar favoritos aunque no jueguen en la fecha seleccionada.
6. Estado: composiciones con iconos para vacío, desconexión, error, carga y datos guardados. Retroalimentación nativa al toque; transición breve del marcador al cambiar, sin bucles permanentes.
7. Navegación: gesto de regreso Wear y acciones de borde cuando correspondan, con la misma estructura funcional. Soporte circular prioritario y compatibilidad con el emulador cuadrado solicitado anteriormente.

## Orden de ejecución

Tema/componentes → Home compilado y observado → pantallas deportivas/datos visuales estructurados → microinteracciones → compilación/pruebas → captura y revisión circular en dos tamaños y cuadrada. Los resultados ejecutados se registrarán al finalizar; esta propuesta no constituye una validación.

## Implementación

Se trabajó en el proyecto existente. Wear Material 3 1.5.0 y la integración HTTP se conservaron; no se añadió Material para teléfonos ni una biblioteca de iconos. El sistema visual está en `presentation/theme` y los componentes en `presentation/components`.

Home se compiló y se observó primero en el emulador cuadrado. Después se sustituyeron los listados y detalles. Próximos usa horarios y filas de equipos; En vivo y Resultados priorizan el marcador. Detalles añade una cronología tipada y comparativas alineadas por ID de equipo. Se conservan las representaciones anteriores de eventos/estadísticas para compatibilidad.

Las barras se dibujan únicamente con ambos valores numéricos disponibles y no negativos. Los datos faltantes se representan con un guion o con un estado de cobertura; los marcadores desconocidos nunca se convierten en cero. Los ejemplos ficticios siguen separados del proveedor y se identifican como DEMO.

Favoritos conserva los IDs previos y un catálogo local acotado de nombres/escudos; los equipos seguidos se retienen al cambiar de fecha. Se corrigió la captura de una lista inicial dentro del grafo de navegación: cada destino calcula los partidos con su estado actual, evitando mezclar el modo DEMO con la etiqueta API.

Las tarjetas responden a la pulsación, el marcador cambia mediante una transición breve y las listas aprovechan las transformaciones nativas. Se respeta movimiento reducido y no se añadieron animaciones infinitas de LIVE ni sondeos de red más frecuentes.

## Validación ejecutada

- `assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL; 22 pruebas, cero fallos. Cubren la lógica previa y la selección del protagonista, abreviaturas, zonas horarias, eventos tipados y estadísticas emparejadas por equipo, incluidos valores ausentes o inválidos.
- Circular: mismo AVD Wear OS 4 de 454 × 454 píxeles, a 320 dpi (~227 dp) y 380 dpi (~191 dp). Son dos tamaños lógicos, no dos relojes físicos. Se revisaron las siete secciones y se ajustaron márgenes, encabezados y tarjetas del tamaño pequeño.
- Detalles: cronología, comparativas de DEMO, actualización del marcador de 1:0 a 2:0 y acción de borde alcanzable. Se corrigió el ajuste de su etiqueta para mantenerla en una línea.
- Favoritos: agregar, reiniciar el proceso, cambiar de fecha y quitar; todos completados en el emulador.
- Sin conexión: modo API sin caché mostró desconexión/error, sin resultados DEMO; recuperación al volver a DEMO y gesto de regreso nativo comprobados.
- Revisión final circular con renderizado por software del emulador. La app conserva su renderizado Compose normal; no se desactivó la aceleración de la aplicación.

| Vista | Evidencia |
| --- | --- |
| Home circular | [227 dp](../../artifacts/qa/premium-round-227-home.png), [191 dp](../../artifacts/qa/premium-round-191-home.png) |
| En vivo | [Marcador](../../artifacts/qa/premium-round-227-live.png) |
| Próximos | [Tarjeta compacta en 191 dp](../../artifacts/qa/premium-round-191-upcoming.png) |
| Resultados | [Estado final](../../artifacts/qa/premium-round-227-results.png) |
| Detalle | [227 dp](../../artifacts/qa/premium-round-227-details.png), [191 dp](../../artifacts/qa/premium-round-191-details.png) |
| Cronología | [Eventos DEMO](../../artifacts/qa/premium-round-227-timeline.png) |
| Estadísticas | [Comparativas DEMO](../../artifacts/qa/premium-round-227-statistics.png) |
| Favoritos | [Equipo seguido](../../artifacts/qa/premium-round-227-favorites-followed.png), [otra fecha](../../artifacts/qa/premium-round-227-favorites-next-day.png), [vacío](../../artifacts/qa/premium-round-227-favorites-empty.png) |
| Acción de borde | [Etiqueta en una línea](../../artifacts/qa/premium-round-191-edge-button.png) |

No se midieron FPS ni consumo de batería en un reloj físico. La cobertura de eventos y estadísticas reales depende del proveedor y del encuentro; no se rellenan datos que la API no entregue.

### Cuadrado y API real

Se conservó el AVD del usuario: 360 × 360 píxeles con escala de 240 dpi (~240 dp), modo API y equipo favorito existente. Se instaló el APK final y se comprobaron las secciones con datos del proveedor.

La prueba de desconexión utilizó la caché real previamente guardada: se mostró **Pendiente de actualizar**. Al recuperar la conexión llegaron datos nuevos. Se añadió un observador de conectividad con liberación en `onCleared`; la desaparición del aviso de desconexión se comprobó sin reiniciar la app y sin agregar sondeos de red.

- [Home cuadrado con API](../../artifacts/qa/premium-square-home.png).
- [En vivo con escudos reales](../../artifacts/qa/premium-square-live.png).
- [Próximos](../../artifacts/qa/premium-square-upcoming.png).
- [Caché real sin conexión](../../artifacts/qa/premium-square-stale-offline.png).
- [Conexión recuperada sin reinicio](../../artifacts/qa/premium-square-network-restored.png).

Los scripts y jerarquías XML de la revisión se encuentran en `artifacts/qa/`. Las consultas usan la integración, caché y límites existentes. No se copiaron credenciales a las capturas, documentación ni código fuente versionable.

La entrega quedó instalada y abierta en **Home, modo API, reloj cuadrado**. El emulador circular de QA se cerró. El buffer de crashes de la comprobación final no mostró entradas.
