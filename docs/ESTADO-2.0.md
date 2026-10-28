# MatchControl 2.0 — estado de entrega

## Implementado

- Módulos mobile, wear y core; se conserva el código y la navegación circular del reloj.
- Aplicación móvil con cinco secciones, boleto, detalle, favoritos, billetera y perfil. Diseño oscuro, acento lima, escudos reales y estados vacíos breves.
- DEMO local explícito: 10,000 créditos, SQLite, apuestas simples/combinadas, cuotas bloqueadas, liquidación, anulaciones y reinicio confirmado. No representa dinero real.
- Supabase activo: Auth, sesión cifrada con Android Keystore, caché persistente, RLS por propietario y operaciones financieras atómicas e idempotentes.
- Misma credencial API-Football existente, almacenada únicamente como secreto del servidor. Móvil y reloj comparten datos a través del backend y Data Layer; no incorporan la clave deportiva.
- Fútbol, baloncesto, béisbol y F1 activos en móvil y reloj, con selección de deporte, marcadores específicos y parlays mixtos. Los mercados y la liquidación se detallan en [Multideporte](MULTIDEPORTE.md).
- Encuentros, horarios, marcadores, eventos, estadísticas y cuotas según la cobertura del proveedor. Las cuotas ausentes no se sustituyen por precios ficticios. El modo DEMO de la misma cuenta permite probar los cuatro deportes con eventos educativos privados; los mercados de F1 se demuestran en ese modo porque el proveedor actual no entrega cuotas de F1.
- Trabajador privado de resultados, programado cada 30 minutos. Consulta fechas con apuestas reales pendientes; respeta caché y presupuesto de consultas. La prueba manual del trabajador respondió 200; el cron está activo. Esto no prueba todavía una ejecución disparada por el cron.
- Cola persistente, réplica acotada y acciones idempotentes de Wearable Data Layer. El reloj consulta saldo y boletos; la confirmación financiera sucede en el móvil.

## Verificación ejecutada

| Alcance | Resultado |
| --- | --- |
| JVM core | 33 pruebas aprobadas |
| JVM reloj, existentes | 31 pruebas aprobadas |
| Android instrumentado | 5 pruebas aprobadas: SQLite, cola/réplica, cifrado Keystore, cambios de cuenta y orden de favoritos |
| PostgreSQL local, PGlite con Auth simulado | 37 pruebas aprobadas |
| Backend alojado, contabilidad con cuenta QA | 25 comprobaciones aprobadas |
| Backend alojado, multideporte | 22 comprobaciones nuevas: datos reales, cuatro simples, parlay mixto, liquidación e idempotencia |
| Auth y proveedor alojados | Acceso 200, consulta de 161 encuentros reales; accesos sin token o inválidos 401 |
| Trabajador privado | Instalación y actualización 200; ningún secreto en la respuesta |
| Interfaz Android real | Cinco secciones, detalle, boleto DEMO, confirmación, persistencia tras reinicio e inicio de sesión remoto |
| Sin conexión y reconexión | Sesión y encuentros reales conservados tras reiniciar; aviso retirado al recuperar internet |
| Interfaz Wear OS circular | APK instalado y abierto; pantalla de créditos y boletos comprobada visualmente |
| Parlay móvil | Dos partidos distintos elegidos desde la interfaz, boleto confirmado y conservado tras reiniciar. Acceso visible para agregar otro partido; límite de diez eventos. |
| Cuenta compartida, interfaz | Favorito añadido desde el móvil y conservado al reiniciar; reloj sin cuenta ni saldo propios antes de vincularse. Capturas actualizadas. |
| Enlace real multideporte | Cuenta, saldo, favoritos y seis boletos recibidos en el reloj; parlay de cuatro deportes abierto allí; favorito eliminado desde el reloj reflejado en el móvil |

La prueba financiera alojada cubrió simples, combinada, victoria, derrota, anulación, reintentos sin doble débito ni doble abono, bloqueo de escritura directa de saldo y reinicio explícito. Utilizó eventos QA identificados como DEMO; no alteró resultados reales.

La cuenta temporal QA se cerró en el teléfono y se eliminó junto con sus eventos educativos al terminar. Las capturas conservan la evidencia, sin contraseñas. El usuario puede entrar con su propia cuenta o usar la demostración local.

## Cuenta compartida y actualizaciones

El móvil es la única autoridad de la cuenta. El reloj ya no crea un saldo ni encuentros DEMO independientes antes del enlace. La demostración local, cuando se utiliza, también pertenece al móvil y se replica al reloj. Cambiar de cuenta o cerrar sesión reemplaza su estado al recibir la actualización; las acciones pendientes de otro propietario se descartan.

La publicación observa los cambios del perfil, favoritos, saldo y apuestas, sin esperar al siguiente sondeo de pantalla. Envía el mismo estado ordenado por MessageClient y DataClient urgente. Las secuencias impiden que un mensaje atrasado reintroduzca los datos de otra cuenta. Las acciones de favoritos también se ordenan, se confirman y se reflejan inmediatamente en la interfaz del reloj mientras esperan respuesta.

El reloj muestra equipos, ligas y encuentros guardados, incluso si sus nombres no están en los partidos del día. En Boletos, cada simple o parlay abre selecciones con partido, mercado, cuota bloqueada, estado y marcador disponible. El resumen conserva hasta treinta boletos, priorizando pendientes, y avisa cuando el historial completo debe consultarse en el móvil.

Las pruebas añadidas cubren cambio de cuenta con revisión menor, mensajes fuera de orden, instalación anterior retirada, descarte de acciones ajenas, favoritos optimistas, acciones atrasadas, conservación de parlays pendientes y límite del resumen. Son pruebas del protocolo y la persistencia; **no sustituyen una prueba del transporte Google entre dispositivos vinculados**.

El enlace real ya se comprobó por Wearable Data Layer: el reloj recibió la misma cuenta, saldo, favoritos y eventos de los cuatro deportes. Después recibió seis boletos de la cuenta QA, incluido un parlay mixto, y permitió abrir sus selecciones. Se añadió un favorito desde el móvil y se quitó desde el reloj; ambos cambios se recibieron sin pulsar Actualizar. La comprobación leyó las réplicas de ambos dispositivos sin inyectar datos. Evidencia: `paired-transport.json`, `paired-multisport.json` y capturas `premium-watch-four-sport-*`.

Los resultados reproducibles y las capturas están en `artifacts/qa/v2/`. Los totales de pruebas y lint se pueden regenerar con `preparar-entrega.py` después de compilar. La evidencia corresponde a esta ejecución; no demuestra cobertura de todos los dispositivos ni disponibilidad permanente del proveedor.

## Pendiente y límites concretos

- **Dispositivos:** la vinculación ya entregó datos reales de la aplicación entre **MatchControl Mobile Play Store** y **MatchControl Round QA**. Después de reiniciar el entorno, Android Studio restableció el puente. La app complementaria de Google puede solicitar completar su configuración; no se automatiza ese acceso. [Guía del enlace](VINCULAR-DISPOSITIVOS.md).
- Las notificaciones locales se muestran al observar la liquidación; no hay entrega push FCM garantizada con la aplicación cerrada. El servidor sí puede liquidar independientemente del móvil.
- La cobertura de ligas, fechas, estadísticas y cuotas depende del plan de API-Football. No se promete información que el proveedor no entregue. La hora visible utiliza la zona local; un partido aplazado o sin estado actualizado puede mostrarse por confirmar.
- Se conserva el código 1.x. Los proveedores activos de los cuatro deportes están centralizados en el servidor; el reloj consume la cuenta y los resúmenes del móvil.
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

## Cierre de la versión del 28 de octubre de 2026

La revisión final añadió protección contra respuestas de sesiones anteriores al cerrar o cambiar de cuenta, incluidas renovaciones de tokens atrasadas. Cuatro pruebas de concurrencia verifican que esos resultados no restauran la identidad anterior.

El boleto conserva una salida segura si un evento desaparece, bloquea la confirmación cuando un mercado se cierra o cambia su cuota y actualiza sus horarios mientras la pantalla está abierta. La portada ofrece crear una jornada DEMO cuando no quedan próximos eventos; conserva saldo, favoritos e historial.

ejecutar-matchcontrol.ps1 compila, instala y abre ambos dispositivos, verifica los AVD y conserva los datos. -SinCompilar utiliza los APK existentes y -SinVentana permite verlos desde Running Devices.

La ejecución final verificó que los APK instalados coinciden por SHA-256 con los de entrega, que ambas aplicaciones están abiertas y que los relojes de los emuladores usan America/Phoenix y coinciden con la hora del equipo. Crear una jornada desde Inicio mantuvo los 9,600 créditos y cuatro boletos existentes; los eventos futuros de los cuatro deportes llegaron al reloj. Evidencia: rtifacts/qa/v2/final-runtime.json y capturas premium-final-*.
