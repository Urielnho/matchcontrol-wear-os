# MatchControl 2.1

Aplicación Android y Wear OS con eventos y resultados reales de fútbol, baloncesto, béisbol y Fórmula 1. Los equipos, calendarios, marcadores, clasificaciones y cuotas proceden de los proveedores deportivos. La aplicación no genera encuentros, resultados ni precios.

Las apuestas y los parlays utilizan créditos virtuales. No hay dinero real, depósitos, retiros ni premios canjeables.

## Ejecutar

Abre esta carpeta en Android Studio y espera a que termine Gradle Sync. Usa el JDK integrado. La configuración pública de Supabase está en local.properties y permanece excluida de Git. La clave deportiva se guarda únicamente en el servidor.

Desde PowerShell, ejecuta:

    .\ejecutar-matchcontrol.ps1

Con los APK ya compilados:

    .\ejecutar-matchcontrol.ps1 -SinCompilar

El script abre MatchControl_Mobile_Play y MatchControl_Round_QA, comprueba los dispositivos y conserva los datos. Permite -SinVentana para usar Running Devices y -ZonaHoraria para cambiar America/Phoenix. El primer enlace se completa con Device Manager → Pair Wearable.

También puedes ejecutar los módulos mobile y wear por separado desde Android Studio. Ambos usan com.matchcontrol y la misma firma, instalados en dispositivos diferentes.

## Usar la aplicación

- Sin iniciar sesión puedes consultar los resultados reales, explorar deportes y guardar favoritos. No se crea un saldo ni una cuenta de práctica.
- En Perfil puedes iniciar sesión o crear tu cuenta. El reloj comparte esa identidad, favoritos, créditos y boletos.
- Para apostar, abre un evento próximo con cuotas publicadas, elige una selección y abre el boleto. Agregar otro partido permite formar un parlay de hasta diez eventos distintos.
- La confirmación necesita una cuenta. Se bloquea si el evento comienza, cierra el mercado o cambia su cuota. Los créditos y la liquidación se controlan en el servidor.
- Un resultado pendiente o no verificado no se inventa ni se liquida manualmente.

## Cobertura real

Fútbol, baloncesto y béisbol utilizan API-SPORTS. Fórmula 1 utiliza Jolpica para calendario y clasificaciones. Solo se ofrecen mercados y cuotas que el proveedor publique y que la aplicación pueda interpretar correctamente.

El proveedor de F1 no ofrece cuotas: se muestran sus carreras y clasificaciones, sin inventar mercados. La cobertura del resto de deportes depende del plan y de la disponibilidad del proveedor. Si no hay datos o cuotas, la pantalla lo indica.

Los resultados públicos no requieren sesión. Las consultas usan caché compartida, fechas e identificadores permitidos y un presupuesto de solicitudes. Las claves privadas no están en los APK.

## Entrega y verificación

Los APK, código y capturas actuales se generan en entrega/MatchControl-2.1 con:

    python preparar-entrega.py

El paquete comprueba que los APK no contienen la clave privada ni los generadores de práctica. Los datos sintéticos utilizados para probar las reglas están solo en fuentes de prueba, fuera de las aplicaciones.

[Estado](docs/ESTADO-2.0.md) · [Guía para presentar](docs/GUIA-PRESENTACION.md) · [Reglas por deporte](docs/MULTIDEPORTE.md) · [Datos reales y servidor](docs/DATOS-REALES.md)
