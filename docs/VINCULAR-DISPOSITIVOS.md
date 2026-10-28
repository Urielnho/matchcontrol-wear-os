# Enlace de los emuladores

Ya están preparados y ejecutados en este equipo:

- **MatchControl Mobile Play Store** (`MatchControl_Mobile_Play`, puerto 5556): Android con Play Store y MatchControl móvil instalado.
- **MatchControl Round QA** (`MatchControl_Round_QA`, puerto 5558): reloj circular con MatchControl instalado.

Se trasladó únicamente la demostración local del teléfono QA anterior: tres boletos, un favorito y el mismo saldo. El emulador anterior conserva su información. No se trasladaron contraseñas ni sesiones de Google o Supabase.

## Configurar el enlace en otra ejecución

1. En el teléfono **MatchControl Mobile Play Store**, abre Play Store e inicia sesión tú mismo si Google lo pide.
2. En Android Studio, abre **Tools → Device Manager**.
3. En el menú **⋮** del teléfono, elige **Pair Wearable**.
4. Selecciona **MatchControl Round QA** y continúa con **Next**. Instala y configura la aplicación complementaria que indique el asistente; comprueba los códigos de vinculación.
5. Abre MatchControl en ambos. En el móvil, cambia un favorito o confirma un boleto. El reloj debe recibir la cuenta del móvil y mostrarlo sin pulsar Actualizar.
6. Desde el reloj, modifica un favorito y verifica el móvil. Abre **Créditos y boletos → Ver selecciones** para revisar el parlay.
7. Desconecta y reconecta para comprobar que se conservan los datos y se envían los cambios pendientes.

La vinculación de este equipo ya quedó confirmada: el reloj recibió la cuenta del móvil, saldo, favoritos y seis boletos, incluido un parlay con los cuatro deportes. También se comprobó quitar un favorito desde el reloj y recibir el cambio en el teléfono. La evidencia está en `artifacts/qa/v2/paired-multisport.json`; no se inyectó estado para simular ese enlace.

El control de ventanas de Windows no está disponible en esta sesión (`Computer Use native pipe is unavailable`, os error 2), por lo que el usuario completó el asistente. El acceso a la cuenta de Google debe realizarlo el usuario. Android Studio guarda el vínculo y restablece su puente al arrancar los dispositivos. Si las ventanas independientes del emulador no responden, pueden ejecutarse sin ventana y verse desde la herramienta Running Devices de Android Studio.

Las instrucciones y los requisitos del teléfono proceden de la [guía oficial de Android](https://developer.android.com/training/wearables/get-started/connect-phone).
