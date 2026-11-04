# CustomDialer

Teléfono para Android con diseño tipo CallApp: contactos y llamadas reales,
teclado en botón flotante y códigos secretos que muestran una imagen.

## Cómo abrirlo
1. Android Studio (Koala o más reciente) -> File > Open -> esta carpeta.
2. Espera el Gradle Sync (necesita internet la primera vez).
3. Conecta tu teléfono con depuración USB y pulsa Run.

## Cómo usarlo como app de teléfono principal
1. Abre la app y acepta los permisos (contactos, llamadas, notificaciones).
2. Pulsa **Activar** en el aviso de arriba y elige CustomDialer como app de teléfono.
3. Desde ese momento, las llamadas entrantes y salientes pasan por esta app.
   Para volver a la de Samsung: Ajustes > Aplicaciones > Apps predeterminadas > App de teléfono.

## Archivos
- `MainActivity.kt`: pantalla principal (buscador, pestañas, favoritos, lista, botón del teclado).
- `Components.kt`: tema, avatares, filas y teclado.
- `CodesScreen.kt`: crear códigos con imagen y mostrar la imagen.
- `InCallActivity.kt`: pantalla de llamada (contestar, rechazar, silenciar, altavoz, espera).
- `Calls.kt`: servicio de llamadas (`InCallService`), notificación de llamada entrante y marcado.
- `Data.kt`: lectura de contactos y registro de llamadas, y base de datos Room de códigos.
- `DialerViewModel.kt`: estado y lógica.

Requiere Android 10 o superior (minSdk 29).
