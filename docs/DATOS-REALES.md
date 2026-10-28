# Backend de resultados reales

La versión 2.1 consulta únicamente eventos, resultados y cuotas publicados por los proveedores deportivos. Se mantienen créditos virtuales para las apuestas: no existen depósitos, retiros ni dinero real.

## Consulta sin iniciar sesión

`POST /functions/v1/scores` recibe la clave publicable en `apikey`; no necesita sesión ni crea una cuenta anónima.

```json
{"action":"events","sport":"FOOTBALL","date":"2026-10-28","timezone":"America/Phoenix"}
```

- `action`: `events`, `details` u `odds`.
- `sport`: `FOOTBALL`, `BASKETBALL`, `BASEBALL` o `FORMULA1`.
- `date`: fecha válida entre siete días antes y siete días después del día actual UTC.
- `timezone`: zona IANA válida; por defecto UTC.
- `id`: obligatorio para detalle y cuotas. Debe ser un evento real ya conocido, del deporte indicado y situado en la ventana próxima al día actual. F1 permite todos los Grandes Premios conocidos de la temporada actual. Los IDs ajenos o inexistentes se rechazan antes de consultar al proveedor.

El resultado conserva el contrato `Snapshot`, con `source=API_FOOTBALL`. El perfil está vacío, el saldo es cero y no se incluyen boletos, movimientos, preferencias ni favoritos de ninguna cuenta. La aplicación combina los catálogos consultados. Para F1 se entrega el calendario de la temporada solicitada y las clasificaciones publicadas.

Las consultas reutilizan la caché compartida de quince minutos y el presupuesto atómico existente de 95 peticiones diarias entre todos los servicios. Una respuesta guardada conserva su fecha de actualización. Si el proveedor falla, se puede devolver información real guardada con `stale=true`; no se genera contenido de sustitución.

## Apuestas y cuenta

El servicio autenticado `sandbox` conserva su nombre técnico para compatibilidad. Sus únicas acciones admitidas son consultar el estado, confirmar un boleto, guardar o quitar un favorito y cambiar la preferencia de notificaciones. Ya no acepta crear jornadas, editar resultados, restablecer saldo ni cambiar a fuentes educativas.

La migración obliga al modo de datos reales y comprueba el origen también dentro de la operación financiera. No basta con esconder controles en la interfaz: una selección de otro origen o la repetición de un boleto educativo anterior se rechaza. El débito y los reintentos de boletos reales mantienen su transacción e idempotencia originales.

Los registros educativos anteriores se conservan internamente para no destruir el historial de auditoría. No se devuelven como encuentros, cuotas, boletos, movimientos asociados ni favoritos en el estado actual. El proceso de liquidación solo trabaja con boletos de eventos reales. Los cambios de marcador únicamente llegan desde la normalización de los proveedores.

## Cobertura

Fútbol, baloncesto y béisbol utilizan la clave API-SPORTS guardada en el servidor. F1 utiliza el calendario y las clasificaciones de Jolpica. Las cuotas solo están disponibles cuando el proveedor las publica para los mercados y líneas compatibles; F1 no dispone de cuotas en el proveedor actual. Una ausencia de cobertura permanece visible como ausencia de datos o de cuotas, sin inventar cifras.

Las cuotas guardadas conservan la hora de su consulta original. Leer la caché no prolonga su validez. Tras treinta minutos no se pueden confirmar y el servidor exige consultarlas de nuevo.

## Verificación

- 50 comprobaciones PostgreSQL locales: 37 de las migraciones históricas y 13 sobre la transición final a eventos reales, privacidad del feed, retiro de operaciones de laboratorio, rechazo de selecciones educativas, boletos reales e invalidación de cuotas antiguas.
- Seis pruebas del feed: cuatro deportes, límites de fechas y zonas, IDs inválidos, evento inexistente y evento fuera de ventana rechazados antes de gastar presupuesto.
- Revisión de tipos de las cinco funciones desplegables con Deno.

Las pruebas locales usan datos aislados de prueba y no alteran resultados deportivos reales. Los resultados de consultas alojadas se guardan por separado en los archivos de comprobación de la versión.

## Consulta alojada del 28 de octubre de 2026

La consulta pública sin sesión devolvió 500 encuentros de fútbol (límite del feed), 138 de baloncesto, 14 de béisbol y 23 carreras de F1. Todos provenían de fuentes reales y se devolvieron sin datos de cuenta ni registros personales. Los campos `stale` fueron falsos en esa consulta.

Se probó una sola muestra de cuotas por deporte de equipos: ocho selecciones compatibles de fútbol (Serie A), dos de baloncesto (NBB) y dos de béisbol (LVBP). F1 devolvió el Gran Premio futuro solicitado y cero cuotas. Estas muestras documentan cobertura en ese momento y no garantizan que todas las competiciones tengan mercados.

También se comprobaron rechazos HTTP para IDs ajenos, eventos inexistentes, fechas fuera de rango y acciones de escritura enviadas al feed. Informe: `artifacts/qa/v2/real-only-live.json`.

Los asesores de seguridad conservaron únicamente dos avisos informativos esperados por la caché y el presupuesto privados, con RLS habilitado y sin políticas para clientes. [Explicación del aviso](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy).
