# Consolidación SENE ADMIN

## Base seleccionada

Se eligió `integration/ack-universal` porque la auditoría mostró que está 45 commits por delante de `main`, sin commits exclusivos pendientes de `main`. Es la rama que contiene la aplicación Android, el cliente de API, la sesión cifrada y el protocolo de eventos con ACK.

## Rama activa

`consolidation/admin-unificada`

## Cambios de consolidación

- La validación de pagos muestra el folio completo de la solicitud antes de autorizar.
- El campo de concepto/referencia se precarga con el folio y la aplicación impide confirmar si no coincide exactamente.
- La lista de solicitudes y la bandeja de notificaciones muestran el folio junto con el negocio.
- Se añadió smoke test separado para instalar y abrir la APK en emulador Android 35.
- Si la aplicación se cierra al iniciar, CI captura Logcat, Activity Manager y estado del paquete para diagnosticar el fallo.
- Las ejecuciones de CI usan grupos independientes para evitar que una compilación nueva cancele otra prueba activa.

## Dependencia de autenticación

El Worker de la rama `integration/ack-universal` contiene `/v1/admin/login`, sesiones revocables y el ACK de eventos administrativos. Para probar el acceso real, el Worker de pruebas necesita los cuatro secretos descritos en `docs/ADMIN_AUTH_SETUP.md`:
- `SENE_TEST_ADMIN_LOGIN_EMAIL`
- `SENE_TEST_ADMIN_PASSWORD_HASH`
- `SENE_TEST_ADMIN_PASSWORD_SALT`
- `SENE_TEST_ADMIN_SESSION_SECRET`

No se guardan contraseñas ni tokens en la APK o en el repositorio. Si esos secretos no están configurados, el Worker debe responder `ADMIN_AUTH_NOT_CONFIGURED` y el inicio de sesión no se considera validado.

## Estado y límites

- La compilación y el smoke test Android de la rama consolidada siguen pendientes de confirmación.
- El inicio de sesión y las operaciones deben probarse contra el Worker/D1 de pruebas con secretos configurados.
- Las autorizaciones de pagos requieren verificar el movimiento bancario real antes de confirmar.
- No se crea PR ni se integra en `main` desde esta rama.
- No se despliega ni modifica producción.
