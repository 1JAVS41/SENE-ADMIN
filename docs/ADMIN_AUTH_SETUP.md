# Configuración del acceso administrativo SENE

## Seguridad implementada

- SENE ADMIN inicia sesión en `POST /v1/admin/login` por HTTPS.
- La contraseña se verifica en el Worker con PBKDF2-HMAC-SHA256, 210 000 iteraciones y salt aleatorio.
- El Worker emite un token HMAC con vigencia de 12 horas y registra la sesión en D1.
- `POST /v1/admin/logout` revoca la sesión.
- SENE ADMIN cifra el token local con AES-GCM y una clave no exportable de Android Keystore.
- El token administrativo está ausente del código fuente y de la APK.
- Los endpoints administrativos antiguos siguen aceptando `ADMIN_TOKEN` para automatizaciones de servidor; la APK no lo contiene ni lo solicita.

## Secretos requeridos en Cloudflare

Configurar exclusivamente en el Worker de pruebas antes de habilitar el inicio de sesión:

| Variable | Contenido |
|---|---|
| `ADMIN_LOGIN_EMAIL` | Correo autorizado para la cuenta central SENE |
| `ADMIN_PASSWORD_HASH` | Hash hexadecimal de 64 caracteres |
| `ADMIN_PASSWORD_SALT` | Salt aleatorio codificado en Base64, mínimo 16 bytes |
| `ADMIN_SESSION_SECRET` | Secreto aleatorio de al menos 32 caracteres |

No guardar estos valores en GitHub, archivos TOML, README, APK ni registros de CI.

## Generación local del hash

En una terminal privada con Node.js, ejecutar el siguiente script. Introducir la contraseña solo en la terminal local; no subirla al repositorio ni compartirla en el chat.

```js
const crypto = require("node:crypto");
const salt = crypto.randomBytes(24);
const password = "SUSTITUIR_LOCALMENTE_POR_LA_CONTRASEÑA";
const hash = crypto.pbkdf2Sync(password, salt, 210000, 32, "sha256");
console.log("ADMIN_PASSWORD_SALT=" + salt.toString("base64"));
console.log("ADMIN_PASSWORD_HASH=" + hash.toString("hex"));
console.log("ADMIN_SESSION_SECRET=" + crypto.randomBytes(48).toString("base64url"));
```

Reemplazar el texto de ejemplo localmente antes de ejecutar. No guardar el archivo con la contraseña real.

## Worker de pruebas

Desde `cloudflare/sene-api`, configurar cada secreto en el entorno de pruebas:

```bash
wrangler secret put ADMIN_LOGIN_EMAIL --config wrangler.testing.toml
wrangler secret put ADMIN_PASSWORD_SALT --config wrangler.testing.toml
wrangler secret put ADMIN_PASSWORD_HASH --config wrangler.testing.toml
wrangler secret put ADMIN_SESSION_SECRET --config wrangler.testing.toml
```

Después desplegar únicamente con `wrangler.testing.toml` o con el workflow de despliegue de pruebas. No ejecutar estos comandos con `wrangler.toml` de producción.

## Estado funcional actual

La APK permite iniciar sesión, guardar la sesión cifrada, cerrar sesión y consultar las bandejas de altas, renovaciones, reposiciones y recuperaciones. Los controles de autorización de pagos, decisiones, auditoría y ACK de eventos administrativos aún deben integrarse antes de considerar SENE ADMIN terminada o apta para pruebas reales.
