# MatchControl 2.0

Android y Wear OS para consultar fútbol, seguir equipos y gestionar apuestas educativas con créditos virtuales. No hay dinero real, depósitos, retiros ni premios canjeables.

## Aplicaciones

- **mobile:** interfaz Material 3, partidos, detalles, mercados, boleto, apuestas, favoritos, perfil y billetera.
- **wear:** conserva el proyecto del reloj en el directorio `app/`, con navegación circular, favoritos, resumen de créditos y boletos, envío de partidos al móvil y sincronización.
- **core:** contratos, motor decimal sandbox, SQLite, sesión cifrada, adaptadores remotos y protocolo Data Layer.

Sin sesión se utiliza **DEMO local**, con partidos y cuotas ficticios identificados. El perfil comienza con 10,000 créditos; permite confirmar y liquidar boletos sin internet mediante el laboratorio separado de las pantallas normales. Los reintentos no duplican descuentos ni retornos.

Con sesión se consulta **Supabase**, que es la autoridad de perfiles, saldo y apuestas. El servidor comparte la caché de API-Football entre móvil y reloj. Los partidos reales y las cuotas disponibles se normalizan; si el plan no ofrece cuotas, se informa sin inventarlas. Los boletos utilizan únicamente créditos virtuales, incluso con encuentros reales.

## Ejecutar en Android Studio

1. Abre esta carpeta y espera a que termine Gradle Sync. Usa el JDK integrado de Android Studio.
2. Copia `local.properties.example` como `local.properties` y configura la URL y la clave **publicable** de tu proyecto. Android Studio registra la ruta del SDK.
3. Selecciona **mobile** y un teléfono Android para ejecutarlo. Selecciona **wear** y un reloj Wear OS para la otra aplicación.
4. Ambas aplicaciones utilizan `com.matchcontrol` y la misma firma de desarrollo, como exige Wearable Data Layer. Se instalan en dispositivos distintos.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :mobile:assembleDebug :wear:assembleDebug
.\gradlew.bat :core:testDebugUnitTest :wear:testDebugUnitTest
```

APK móvil: `mobile/build/outputs/apk/debug/mobile-debug.apk`. APK reloj: `app/build/outputs/apk/debug/wear-debug.apk`.

En el móvil, **Perfil** permite iniciar sesión o crear una cuenta. Si el proyecto exige confirmar el correo, hazlo antes de entrar. Sin sesión puedes probar DEMO; su saldo se conserva por separado del saldo de Supabase. La sesión remota se cifra con Android Keystore y no se envía al reloj.

## Armar un parlay

Elige una cuota de un próximo partido y abre el boleto. Pulsa **Agregar otro partido · crear parlay**, selecciona un mercado de otro encuentro y vuelve al boleto. Con dos o más selecciones aparece **Parlay**, con la cuota total y el retorno potencial. Admite hasta diez partidos distintos; se confirma un solo importe de créditos virtuales. Las cuotas reales dependen de la cobertura del proveedor. En DEMO, partidos y cuotas son ficticios y están identificados.

## Supabase y proveedor deportivo

El proyecto configurado es `vxgvhptsxjjixhaldvor` (`reloj`). La configuración pública del cliente permanece en `local.properties`. La clave deportiva existente se configura como secreto `FOOTBALL_API_KEY` de Edge Functions, nunca como credencial Android.

`supabase/migrations/` contiene tablas, índices, RLS, restricciones y operaciones atómicas. Las funciones usan `@supabase/server` con autenticación HTTP; toman el usuario del JWT validado, no de un ID libre enviado por el cliente. El cliente administrativo se reserva a las operaciones privilegiadas ya autorizadas.

- `sandbox`: estado de cuenta, boletos, reinicio explícito del saldo y favoritos.
- `football`: proxy, caché, cuota diaria y normalización de fixtures y cuotas.
- `settle-sandbox`: proceso privado de resultados DEMO y reconciliación idempotente. Requiere una clave secreta del servidor; no es accesible al cliente móvil.

Las variables alojadas `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEYS`, `SUPABASE_SECRET_KEYS` y `SUPABASE_JWKS` las resuelve el paquete de servidor. No copies claves administrativas al móvil, al reloj ni a Git. Los ejemplos no contienen secretos.

Las consultas al proveedor se realizan solo desde el backend. La cobertura depende del plan. El sistema rechaza cuotas modificadas, cerradas o caducadas, combinadas de un mismo evento y mezclas entre eventos reales y DEMO. Los resultados deportivos se liquidan con el marcador reglamentario verificado; si falta, el boleto permanece pendiente.

## Reloj y conexión

Inicia sesión solo en el móvil: el reloj utiliza esa misma cuenta, saldo, favoritos y boletos, sin otra contraseña ni cartera independiente. Antes del primer enlace muestra “Vincula tu móvil”. Los cambios del móvil se publican automáticamente, con mensajes inmediatos cuando hay vínculo y una copia persistente para reconexión. El reloj permite consultar cada selección del boleto, su cuota, estado, importe y retorno; las confirmaciones financieras se realizan en el móvil.

Vincula los dispositivos mediante **Device Manager → Pair Wearable** (o el procedimiento del reloj físico). El teléfono del asistente requiere Android 11 o posterior y Play Store. Tener dos emuladores abiertos no significa que estén vinculados. DataClient conserva el estado y MessageClient lleva actualizaciones y acciones inmediatas. SQLite guarda la réplica y la cola del reloj durante una desconexión. Las acciones incluyen identificador, cuenta y secuencia; los cambios atrasados no pueden restaurar una cuenta anterior ni deshacer el último favorito. No se copian contraseñas ni tokens de sesión al reloj. [Requisitos oficiales de vinculación](https://developer.android.com/training/wearables/get-started/connect-phone).

## Pruebas y estado

```powershell
npm ci --prefix backend-tests
npm test --prefix backend-tests
```

Las pruebas PostgreSQL usan PGlite con Auth simulado. Las comprobaciones del servicio alojado están documentadas por separado y no se confunden con esas pruebas locales. [Auditoría inicial](docs/AUDITORIA-2.0.md), [estado y verificación](docs/ESTADO-2.0.md), [evidencia de la app](artifacts/qa/v2/).

Los documentos 1.x permanecen en `docs/historial/`. El código de los proveedores multideporte anteriores se conserva; no se ejecuta como proveedor independiente del reloj en 2.0.
