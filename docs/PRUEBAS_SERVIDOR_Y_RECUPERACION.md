# Preparar entorno de pruebas SENE ADMIN

La aplicación de esta rama apunta exclusivamente a:

`https://sene-licensing-api-testing.1codex41.workers.dev`

Nunca cambiar esta dirección por la de producción en una compilación de pruebas.

## Secretos necesarios para recuperación por correo

En el repositorio **1JAVS41/SENE-PATRON** abre:

Settings → Secrets and variables → Actions → New repository secret

Añade los siguientes nombres:

- `SENE_TEST_RESEND_API_KEY`: API key de Resend con permiso de envío.
- `SENE_TEST_RESEND_FROM_EMAIL`: remitente autorizado/verificado en Resend, por ejemplo `SENE Pruebas <pruebas@tu-dominio-verificado>` si el proveedor lo acepta como formato de remitente.

El Worker de pruebas también requiere los cuatro secretos administrativos ya previstos en ese repositorio:

- `SENE_TEST_ADMIN_LOGIN_EMAIL`
- `SENE_TEST_ADMIN_PASSWORD_HASH`
- `SENE_TEST_ADMIN_PASSWORD_SALT`
- `SENE_TEST_ADMIN_SESSION_SECRET`

No introducir secretos en el código, APK, issues, commits ni conversaciones. La API key de Resend nunca se incluye en la aplicación Android.

## Flujo de despliegue

Al actualizar la rama `fix/admin-password-recovery-testing` en SENE-PATRON, GitHub Actions aplica las migraciones a D1 de pruebas, verifica la tabla de recuperación y despliega el Worker de pruebas. No se ejecuta contra producción.

## Flujo en la APK

1. Introducir correo y nueva contraseña (mínimo 12 caracteres) dos veces.
2. Solicitar código al servidor.
3. Introducir el código de ocho dígitos recibido por correo.
4. Confirmar; el código vence a los 15 minutos y solo puede usarse una vez.
5. Iniciar sesión con la nueva contraseña.

La APK inicial de esta rama incluye login, recuperación y verificación de disponibilidad del servidor. El resto de módulos administrativos se incorporará y validará antes de declarar completa la aplicación.
