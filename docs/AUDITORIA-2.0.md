# Fase 1 — auditoría antes de cambios (24 octubre 2026)

El proyecto original tiene un módulo Android `app`, Kotlin 2.2.10, AGP 8.13.0, SDK 36/JDK 17, Compose BOM 2025.08.00 y Wear Material 3 1.5.0. Se conservan estas versiones para evitar una migración de dependencias ajena al objetivo.

Pantallas: inicio, en vivo, próximos, resultados, favoritos, ajustes, detalle de fútbol, selector de deportes, calendario/resultados multideporte y detalle. Navegación SwipeDismissableNavHost, TransformingLazyColumn, componentes circulares y tema negro/lima. No se reconstruye el reloj.

Datos: ApiRepository (Retrofit/OkHttp, fútbol), SportsRepository (baloncesto, béisbol y Jolpica/F1), mappers por deporte, cachés JSON, cuotas diarias, SettingsStore/DataStore, MatchNotifications. Estado de presentación en AndroidViewModel/StateFlow. Existían 31 pruebas JVM de reglas, mappers y avisos. No existía inyección global, aplicación móvil, Data Layer, base de datos de apuestas, Auth ni Supabase.

Aspectos a corregir para 2.0: la credencial deportiva se compilaba en el APK; reloj y futuro móvil no deben consultar independientemente el proveedor. Los favoritos eran locales al reloj. Los avisos funcionaban solo al actualizar durante el uso. La cobertura por temporada depende del plan. El modo 2.0 debe funcionar sin credenciales y sin consultar resultados reales; su DEMO debe distinguirse permanentemente.

Decisión: `wear` utiliza el directorio original `app/`, manteniendo su aplicación e identidad. Se añaden `mobile` y `core`. Core contiene contratos, reglas decimales, persistencia SQLite y protocolo de sincronización. El móvil es la autoridad local del sandbox; el reloj conserva una réplica y una cola de acciones idempotentes, sin confirmar boletos ni debitar saldos. Se mantienen los adaptadores deportivos antiguos sin ejecutarlos en DEMO. Supabase se prepara mediante migraciones y funciones, sin conectar proyectos ajenos ni pedir claves.
