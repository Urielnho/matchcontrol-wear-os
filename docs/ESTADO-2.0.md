# MatchControl 2.0 — estado de entrega

## Implementado

- Módulos mobile, wear y core; se conserva el código y la navegación circular del reloj.
- Aplicación móvil con cinco secciones, boleto, detalle, favoritos, billetera y perfil. Diseño oscuro, acento lima, escudos reales y estados vacíos breves.
- DEMO local explícito: 10,000 créditos, SQLite, apuestas simples/combinadas, cuotas bloqueadas, liquidación, anulaciones y reinicio confirmado. No representa dinero real.
- Supabase activo: Auth, sesión cifrada con Android Keystore, caché persistente, RLS por propietario y operaciones financieras atómicas e idempotentes.
- Misma credencial API-Football existente, almacenada únicamente como secreto del servidor. Móvil y reloj comparten datos a través del backend y Data Layer; no incorporan la clave deportiva.
- Encuentros, horarios, marcadores, eventos, estadísticas y cuotas según la cobertura del proveedor. Las cuotas ausentes no se sustituyen por precios ficticios. Los mercados reglamentarios requieren un marcador reglamentario verificado.
- Trabajador privado de resultados, programado cada 30 minutos. Consulta fechas con apuestas reales pendientes; respeta caché y presupuesto de consultas. La prueba manual del trabajador respondió 200; el cron está activo. Esto no prueba todavía una ejecución disparada por el cron.
- Cola persistente, réplica acotada y acciones idempotentes de Wearable Data Layer. El reloj consulta saldo y boletos; la confirmación financiera sucede en el móvil.

## Verificación ejecutada

| Alcance | Resultado |
| --- | --- |
| JVM core | 20 pruebas aprobadas |
| JVM reloj, existentes | 31 pruebas aprobadas |
| Android instrumentado | 5 pruebas aprobadas: SQLite, cola/réplica, cifrado Keystore, cambios de cuenta y orden de favoritos |
| PostgreSQL local, PGlite con Auth simulado | 24 pruebas aprobadas |
| Backend alojado, contabilidad con cuenta QA | 25 comprobaciones aprobadas |
| Auth y proveedor alojados | Acceso 200, consulta de 161 encuentros reales; accesos sin token o inválidos 401 |
| Trabajador privado | Instalación y actualización 200; ningún secreto en la respuesta |
| Interfaz Android real | Cinco secciones, detalle, boleto DEMO, confirmación, persistencia tras reinicio e inicio de sesión remoto |
| Sin conexión y reconexión | Sesión y encuentros reales conservados tras reiniciar; aviso retirado al recuperar internet |
| Interfaz Wear OS circular | APK instalado y abierto; pantalla de créditos y boletos comprobada visualmente |
| Parlay móvil | Dos partidos distintos elegidos desde la interfaz, boleto confirmado y conservado tras reiniciar. Acceso visible para agregar otro partido; límite de diez eventos. |
| Cuenta compartida, interfaz | Favorito añadido desde el móvil y conservado al reiniciar; reloj sin cuenta ni saldo propios antes de vincularse. Capturas actualizadas. |

La prueba financiera alojada cubrió simples, combinada, victoria, derrota, anulación, reintentos sin doble débito ni doble abono, bloqueo de escritura directa de saldo y reinicio explícito. Utilizó eventos QA identificados como DEMO; no alteró resultados reales.

La cuenta temporal QA se cerró en el teléfono y se eliminó junto con sus eventos educativos al terminar. Las capturas conservan la evidencia, sin contraseñas. El usuario puede entrar con su propia cuenta o usar la demostración local.

## Cuenta compartida y actualizaciones

El móvil es la única autoridad de la cuenta. El reloj ya no crea un saldo ni encuentros DEMO independientes antes del enlace. La demostración local, cuando se utiliza, también pertenece al móvil y se replica al reloj. Cambiar de cuenta o cerrar sesión reemplaza su estado al recibir la actualización; las acciones pendientes de otro propietario se descartan.

La publicación observa los cambios del perfil, favoritos, saldo y apuestas, sin esperar al siguiente sondeo de pantalla. Envía el mismo estado ordenado por MessageClient y DataClient urgente. Las secuencias impiden que un mensaje atrasado reintroduzca los datos de otra cuenta. Las acciones de favoritos también se ordenan, se confirman y se reflejan inmediatamente en la interfaz del reloj mientras esperan respuesta.

El reloj muestra equipos, ligas y encuentros guardados, incluso si sus nombres no están en los partidos del día. En Boletos, cada simple o parlay abre selecciones con partido, mercado, cuota bloqueada, estado y marcador disponible. El resumen conserva hasta treinta boletos, priorizando pendientes, y avisa cuando el historial completo debe consultarse en el móvil.

Las pruebas añadidas cubren cambio de cuenta con revisión menor, mensajes fuera de orden, instalación anterior retirada, descarte de acciones ajenas, favoritos optimistas, acciones atrasadas, conservación de parlays pendientes y límite del resumen. Son pruebas del protocolo y la persistencia; **no sustituyen una prueba del transporte Google entre dispositivos vinculados**.

Las pantallas pobladas de favoritos y detalle de boleto del reloj todavía requieren validar visualmente con una réplica recibida por el enlace real; no se inyectaron datos para presentarlos como una sincronización comprobada.

Los resultados reproducibles y las capturas están en `artifacts/qa/v2/`. Los totales de pruebas y lint se pueden regenerar con `preparar-entrega.py` después de compilar. La evidencia corresponde a esta ejecución; no demuestra cobertura de todos los dispositivos ni disponibilidad permanente del proveedor.

## Pendiente y límites concretos

- **Completar el vínculo y comprobar el transporte de extremo a extremo.** Ya se creó y ejecutó **MatchControl Mobile Play Store**, con una imagen Google Play y el APK móvil; el reloj circular también está abierto. Se comprobaron la presencia de Play Store y la restauración de tres boletos DEMO, un favorito y el saldo del teléfono anterior. Falta el paso interactivo de acceso a Google, aplicación complementaria y Device Manager → Pair Wearable. El control de ventanas de Android Studio no está disponible en esta sesión. [Guía para completar el enlace](VINCULAR-DISPOSITIVOS.md). Tener ambos emuladores abiertos no establece la vinculación.
- Las notificaciones locales se muestran al observar la liquidación; no hay entrega push FCM garantizada con la aplicación cerrada. El servidor sí puede liquidar independientemente del móvil.
- La cobertura de ligas, fechas, estadísticas y cuotas depende del plan de API-Football. No se promete información que el proveedor no entregue. La hora visible utiliza la zona local; un partido aplazado o sin estado actualizado puede mostrarse por confirmar.
- El código multideporte 1.x permanece conservado; en 2.0 el proveedor activo centralizado es fútbol.
- Los APK de entrega son de desarrollo, adecuados para evaluación e instalación local. Una publicación comercial requeriría firma de distribución, políticas de privacidad y validación adicional.

## Reproducir

Usar JDK integrado de Android Studio y ejecutar desde la raíz:

```powershell
.\gradlew.bat :mobile:assembleDebug :wear:assembleDebug :core:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug
npm ci --prefix backend-tests
npm test --prefix backend-tests
python preparar-entrega.py
```

Las pruebas alojadas requieren acceso administrativo de desarrollo y una cuenta QA separada. `live-smoke.py --financial` modifica exclusivamente sus eventos educativos; no se debe ejecutar como prueba genérica contra cuentas personales.

Las migraciones locales coinciden con las versiones aplicadas al proyecto alojado. No es necesario volver a crear tablas. Los secretos del backend se configuran en Supabase. `local.properties` queda excluido de Git y del paquete; las aplicaciones solo reciben la URL y la clave pública de Supabase.
