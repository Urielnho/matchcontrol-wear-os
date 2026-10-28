# Vincular móvil y reloj

La instalación de este equipo utiliza MatchControl_Mobile_Play (5556) y MatchControl_Round_QA (5558). Las dos aplicaciones tienen el identificador com.matchcontrol y la misma firma. La consulta de resultados reales y los favoritos se comparten; los créditos y boletos aparecen al iniciar sesión en el móvil.

## Primera vinculación

1. Abre Tools → Device Manager en Android Studio.
2. En el menú del teléfono, elige Pair Wearable.
3. Selecciona el reloj circular y completa el asistente. Si Google solicita autenticación, realízala tú mismo y verifica los códigos de enlace.
4. Abre MatchControl en ambos. Guarda un evento real como favorito desde el móvil y comprueba que llegue al reloj.
5. Desde el reloj, modifica un favorito y comprueba el móvil. Las acciones pendientes se conservan durante una desconexión.

El reloj utiliza la cuenta del móvil y no pide otra contraseña. Los tokens de sesión no se copian al reloj. Los boletos se confirman en el teléfono y se consultan en el reloj.

## Volver a ejecutar

    .\ejecutar-matchcontrol.ps1 -SinCompilar

El script abre las ventanas independientes y restablece el puente de los emuladores ya vinculados. En la configuración preparada, PuertoVinculo es 6660. En otra vinculación, Android Studio puede negociar otro puerto; utiliza Pair Wearable y, si corresponde, indica -PuertoVinculo con ese valor. El script no crea ni sustituye el enlace inicial.

Con -SinVentana se pueden visualizar desde Running Devices. Para evitar saturar la memoria, no es necesario mantener Android Studio abierto mientras se usan las ventanas independientes.

Las réplicas antiguas de práctica se excluyen al actualizar. La aplicación solo muestra los eventos reales del móvil o solicita abrirlo para cargar resultados.

[Requisitos oficiales de Android](https://developer.android.com/training/wearables/get-started/connect-phone).
