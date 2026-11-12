# SENE-ADMIN

Aplicación Android de administración central del ecosistema SENE.

## Alcance
- Recibir alertas administrativas, incluidas licencias vencidas y próximas a vencer.
- Revisar solicitudes de alta, renovación, reposición y recuperación.
- Verificar pagos manualmente contra el movimiento bancario real y registrar la decisión.
- Administrar licencias, empresas y los tres espacios de equipo por empresa.
- Mantener auditoría de cada acción administrativa.

## Separación de información
SENE-ADMIN NO recibe inventarios, ventas, reportes laborales ni nómina. Esa información corresponde exclusivamente a SENE PATRÓN. SENE-ADMIN recibe únicamente eventos administrativos de licencias y solicitudes que requieren intervención autorizada.

## Seguridad
La aplicación no contiene claves de operador ni secretos del servidor. Toda acción pasa por la API autenticada. Las notificaciones push solo despiertan/sincronizan la bandeja: el evento durable permanece en el servidor hasta su ACK. Un comprobante no confirma automáticamente un pago.

## Documento maestro

La especificación consolidada de funcionamiento de las tres APK, backend, ramas auditadas, integración y ahorro de tráfico está en [`docs/ARQUITECTURA_MAESTRA_SENE.md`](docs/ARQUITECTURA_MAESTRA_SENE.md).

## Estado
Inicio del proyecto Android. La interfaz y los módulos se implementarán por etapas, con builds de pruebas verificables antes de considerar una APK lista.