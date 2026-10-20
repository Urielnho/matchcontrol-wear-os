# Validación de fechas, resultados y cobertura

Comprobación: 19 de octubre de 2026. Evidencia reproducible en `artifacts/qa/audit_live_data.py`; respuestas recibidas y resumen en `artifacts/qa/data-audit/`. Ninguna credencial ni información personal de la cuenta se incluye en ese resumen.

## Qué se comprobó

- Fútbol: consulta directa por fecha del 19 de octubre, **198 encuentros**, sin errores. Los **198 IDs y fechas** de la respuesta guardada para GMT coincidieron con la respuesta recién consultada. Se comprobó también el 18 de octubre: 1304 encuentros. Son cantidades por día GMT, no por día local de Phoenix.
- Baloncesto: 50 encuentros para el 19 y 221 para el 18, consultados por día UTC, sin errores. Soles–Abejas figura con resultado final **102–58**. Un resultado de las 00:00 UTC del 19 corresponde a las 17:00 del 18 en Phoenix; el filtro local de la app lo coloca en el día anterior.
- Béisbol: 13 encuentros para el 19 y 18 para el 18, consultados por día UTC, sin errores. Mayos–Hermosillo figura con resultado final **6–10**. La comprobación confirma lo recibido del proveedor; no se contrastó cada partido con una fuente independiente.
- Los detalles por ID de un encuentro de cada deporte respondieron sin errores: fútbol 1557436, baloncesto 525709 y béisbol 186215.
- F1: Jolpica devolvió **23 carreras** de calendario y la última clasificación publicada. Singapur, 11 de octubre: **Verstappen, Leclerc y Antonelli**, coincidente con los [resultados oficiales de F1](https://www.formula1.com/en/racing/2026/singapore).

## Contraste independiente de horarios

El [club Tottenham](https://www.tottenhamhotspur.com/news/1085973/fixture-changes-october) anuncia el partido contra Coventry para el **19 de octubre de 2026 a las 20:00 del Reino Unido**. El servicio devuelve **19:00 UTC**, el mismo instante. En **America/Phoenix** corresponde a las **12:00**.

El emulador estaba configurado en **GMT**. Se cambió su zona a **America/Phoenix**, sin modificar el instante del reloj ni las fechas recibidas. La app utiliza la zona del dispositivo y se reinició para aplicar la conversión. La captura `horario-local-validado.png` registra la pantalla resultante.

El GP de México está programado para el **1 de noviembre de 2026 a las 14:00 en Ciudad de México**, equivalente a **20:00 UTC**, tanto en Jolpica como en el [horario oficial de F1](https://www.formula1.com/en/latest/article/formula-1-gran-premio-de-la-ciudad-de-mexico-2026.14b3wTWzNNLJ5Wd8cG7HCQ).

## Qué permite la cuenta

Los servicios de fútbol, baloncesto y béisbol informaron **Free activo, 100 consultas diarias por servicio**. Esto no implica cobertura completa de toda competición y temporada. El [proveedor](https://www.api-football.com/pricing) documenta limitaciones de temporadas en Free.

Se verificó una diferencia concreta: fútbol permitió consultar los encuentros actuales por fecha y un detalle por ID, pero rechazó `leagues?id=39&season=2026` por acceso a temporada. El mensaje recibido limita esa consulta a 2022–2024. Por tanto, que una consulta funcione no demuestra acceso a todos los filtros ni a todos los datos de 2026.

El servicio Formula-1 de API-Sports también rechazó `races?season=2026` por el plan. La app ya utiliza **Jolpica** para F1 y no depende de esa consulta restringida.

## Alcance de la garantía

La app presenta respuestas de servicios reales y convierte sus horas al huso del reloj. Esta auditoría confirma las muestras indicadas; no certifica cada registro del proveedor. Se conserva información guardada y puede haber retrasos entre actualizaciones. Los detalles no publicados permanecen ausentes, y se informa cuando los periodos no coinciden con el marcador. No se reconstruyen goles, puntos ni clasificaciones para completar datos.
