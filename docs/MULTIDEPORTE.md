# Apuestas educativas en cuatro deportes

Todos los boletos usan créditos virtuales. No hay dinero real ni premios canjeables.

| Deporte | Mercados implementados | Resultado que liquida |
| --- | --- | --- |
| Fútbol | 1X2, doble oportunidad, goles 1.5/2.5, ambos anotan | Tiempo reglamentario verificado, sin prórroga ni penaltis |
| Baloncesto | Ganador sin empate, total de puntos 215.5 | Marcador final, incluida la prórroga |
| Béisbol | Ganador, total de carreras 8.5 | Marcador final, incluidas entradas extra. Un empate oficial anula el ganador |
| F1 | Ganador de carrera, podio, top 10 | Clasificación final de la carrera principal. No incluye sprint |

Un resultado no verificado o incompleto permanece pendiente. Un piloto no clasificado pierde; una posición mayor que diez pierde el top 10. La parrilla DEMO contiene doce pilotos. Una carrera anulada devuelve el importe; en un parlay, su cuota se elimina y se recalcula el retorno. Si todas las selecciones se anulan, se devuelve todo el importe.

Las cuotas se multiplican sin redondear entre selecciones. El retorno se trunca a dos decimales al pagar. La interfaz muestra hasta seis decimales de la cuota combinada y señala con «≈» cualquier aproximación adicional; el cálculo utiliza las cuotas bloqueadas completas.

## Reales y demostración

En **Perfil → Resultados** se puede alternar entre **Reales** y **Demostración** usando la misma cuenta de Supabase. El modo DEMO identifica sus eventos y cuotas ficticios. Cada cuenta tiene sus propios eventos educativos y solo puede resolverlos ella misma. El saldo continúa siendo virtual en ambos modos y el historial conserva la fuente de cada boleto.

Sin sesión también existe una demostración local, conservada por separado, que se replica al reloj. Actualizar la aplicación conserva su saldo, boletos y favoritos.

La clave API-SPORTS existente funciona con los servicios de fútbol, baloncesto y béisbol. Solo el servidor la utiliza. Las consultas alojadas verificaron 143 encuentros de baloncesto y 32 de béisbol entre dos fechas UTC, usadas para cubrir el día local. Jolpica respondió con 23 carreras del calendario y la última clasificación de F1. Estas cifras documentan una consulta concreta, no garantizan cobertura futura.

Las cuotas reales se importan exclusivamente cuando el proveedor las entrega para un mercado compatible. Los totales usan únicamente las líneas indicadas en la tabla. No se transforma una cuota de otra línea o período en una diferente. El proveedor actual de F1 entrega calendario y clasificaciones, **no cuotas de apuestas**; los tres mercados de F1 pueden demostrarse con eventos DEMO. No se presentan cuotas calculadas o ficticias como reales.

## Demostración para el profesor

1. En Perfil, activa **Demostración**; sin sesión ya está activo el modo local.
2. Si los eventos anteriores ya comenzaron, pulsa **Crear jornada DEMO** en Inicio o usa **Laboratorio DEMO → Crear nueva jornada DEMO**. Se conservan tu saldo y tus boletos anteriores.
3. En Partidos, elige Fútbol, Baloncesto, Béisbol y F1. Selecciona un mercado de cada evento; usa **Agregar otro partido** para volver al listado.
4. El boleto debe mostrar **Parlay · 4 partidos**, la cuota multiplicada y un solo importe de créditos virtuales. Confírmalo.
5. En el laboratorio, resuelve fútbol, baloncesto y béisbol. El parlay debe continuar pendiente hasta completar la carrera.
6. En F1, ordena los pilotos con **Subir** y confirma la clasificación. El sistema liquida el boleto una sola vez.
7. En el reloj vinculado, consulta **Créditos y boletos → Ver selecciones**. Cada selección conserva deporte, partido, mercado, cuota y estado.

Solo se admite una selección por evento, hasta diez eventos distintos. Un parlay puede combinar deportes, pero no eventos reales con ficticios. Una cuota cambiada o un evento que ya comenzó se rechazan antes de descontar créditos.

## Evidencia

- Pruebas Kotlin: reglas deportivas, prórroga, empate, clasificación incompleta, piloto no clasificado, top 10, parlay mixto, anulación y migración sin cambiar el saldo.
- PostgreSQL: 37 pruebas aprobadas, incluidas propiedad de eventos DEMO, privacidad de mercados, parlay mixto, liquidación exacta e idempotencia.
- Supabase alojado: 22 comprobaciones aprobadas con una cuenta QA y cinco boletos (cuatro simples y un parlay de cuatro deportes). El parlay retornó exactamente 1,392.95 créditos para 100 apostados a cuotas 1.85 × 1.85 × 1.85 × 2.20.
- Data Layer real: coincidencia de cuenta, saldo, favoritos y seis boletos de QA, incluido un parlay de cuatro deportes abierto en el reloj. Se comprobó añadir un favorito desde el teléfono, quitarlo desde el reloj y cerrar la cuenta QA, recuperando la demostración original en ambos. No se inyectaron datos para simular el enlace. Capturas y verificaciones en `artifacts/qa/v2/`.

La revisión de seguridad conserva dos avisos informativos por caché privada sin políticas de acceso de clientes. También señala que la protección contra contraseñas filtradas está desactivada en la configuración de Auth; no se modificó esa preferencia. [Documentación de esa opción](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).
