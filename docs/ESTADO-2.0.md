# Estado de MatchControl 2.1 — solo datos deportivos reales

Esta versión sustituye los modos anteriores de práctica. No genera equipos, encuentros, marcadores ni cuotas. Las fuentes de ejemplo se han trasladado a las pruebas y no se empaquetan en los APK.

## Comportamiento

- Consulta pública de fútbol, baloncesto, béisbol y F1, con fechas y estados del proveedor.
- Cuenta de Supabase para confirmar apuestas y consultar créditos virtuales, historial y boletos. No hay dinero real.
- Parlays de hasta diez eventos distintos cuando existen cuotas reales compatibles.
- Favoritos compartidos con Wear OS y réplica de cuenta, créditos y boletos.
- Sin laboratorio, cambio a demostración, reinicio de saldo ni edición manual de resultados.
- Los registros antiguos de práctica se excluyen tanto en el servidor como en las réplicas y cachés del cliente. Se conserva un archivo de actualización para no destruir el historial contable anterior.

## Cobertura y límites

Las cuotas dependen del plan y del proveedor. F1 ofrece calendario y clasificación, sin cuotas de apuestas. Ante una consulta sin cobertura o una cuota ausente, la aplicación lo informa y no crea un sustituto.

Los eventos se muestran en la zona del dispositivo. El script de este equipo configura America/Phoenix y permite cambiarla. Un aplazamiento o resultado incompleto sigue pendiente hasta que se verifique.

## Comprobaciones

Las pruebas cubren filtrado de registros antiguos, separación entre datos públicos y cuentas, persistencia real, bloqueo de operaciones financieras locales, cuotas cerradas, reglas por deporte, exactitud decimal, cambios de cuenta y sincronización ordenada.

Los resultados de compilación, pruebas y comprobación de APK se generan con preparar-entrega.py en entrega/MatchControl-2.1/COMPROBACION.json. La evidencia de esta revisión usa los archivos real-only-* de artifacts/qa/v2; las capturas históricas de otras versiones no forman parte de la entrega actual.

    .\gradlew.bat :core:testDebugUnitTest :wear:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug :mobile:lintDebug :wear:lintDebug
    npm test --prefix backend-tests
    python preparar-entrega.py

Los APK son compilaciones de desarrollo para evaluación e instalación local.

## Verificación de esta revisión

La compilación de móvil y reloj y la revisión estática finalizaron sin errores. Pasaron 70 pruebas Kotlin, 6 pruebas Android, 50 comprobaciones PostgreSQL y 6 pruebas de la consulta pública.

La consulta alojada del 28 de octubre de 2026 devolvió 500 encuentros de fútbol (límite de respuesta), 138 de baloncesto, 14 de béisbol y 23 carreras de F1. Las muestras de cuotas devolvieron ocho selecciones de fútbol, dos de baloncesto y dos de béisbol. F1 devolvió cero cuotas. Estos números acreditan las consultas realizadas, sin garantizar cobertura permanente.

El paquete comprueba que no hay credenciales privadas ni clases generadoras de práctica en los APK. El detalle de la comprobación alojada está en artifacts/qa/v2/real-only-live.json.
