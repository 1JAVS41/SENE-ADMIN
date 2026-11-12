# Protocolo ACK universal del ecosistema SENE

Estado: contrato técnico de integración en desarrollo  
Fecha: 2026-11-12  
Ámbito: SENE VENTAS, SENE PATRÓN, SENE ADMIN y Cloudflare Worker/D1.

## 1. Regla obligatoria

Todo intercambio que transfiera información, produzca un cambio de estado o entregue un resultado que el cliente deba conservar tendrá confirmación ACK explícita, durable e idempotente. Ningún componente supondrá que una operación terminó solo porque recibió HTTP 2xx, se cerró una pantalla o se envió una notificación push.

El ACK confirma una condición concreta: el receptor guardó y validó de forma durable el contenido o resultado identificado. No significa que el usuario haya leído, aprobado o ejecutado el contenido, salvo que esa acción de negocio sea precisamente el ACK separado.

No se enviará un ACK individual por cada fila si se puede confirmar un lote con un único ACK. La confirmación por lotes reduce solicitudes y bytes sin perder trazabilidad.

## 2. Qué debe confirmarse

| Flujo | Quién confirma | Momento del ACK |
|---|---|---|
| Solicitudes laborales de VENTAS hacia PATRÓN | Worker confirma recepción a VENTAS; PATRÓN confirma entrega al guardar el lote | Tras persistir la solicitud y, por separado, tras guardar el inbox local |
| Decisiones de PATRÓN hacia VENTAS | Worker confirma recepción a PATRÓN; VENTAS confirma entrega | Después de guardar la decisión localmente |
| Correcciones y autorizaciones de un solo uso | Cada lado confirma recepción durable; el consumo de autorización conserva su propio resultado idempotente | Después de guardar el grant/resultado; nunca antes |
| Reportes compactos de cierre y nómina | Worker confirma recepción a VENTAS; PATRÓN confirma recepción al guardar el reporte | Tras persistencia local verificada |
| Snapshots diarios de inventario | Worker confirma recepción a VENTAS; PATRÓN confirma el paquete de día de pago | Solo después de guardar y validar el paquete completo de ambos equipos |
| Bandeja de eventos administrativos | SENE ADMIN confirma cada lote o cursor | Después de guardar eventos localmente; abrir/ver no equivale a ACK |
| Pagos, altas, renovaciones, reposiciones y recuperaciones | Worker devuelve recibo idempotente; la app confirma que guardó el resultado sensible | Tras persistir folio, estado y código cifrado cuando corresponda |
| Configuración, licencia, sesión y estados descargados | Cliente confirma la versión/cursor aplicado | Después de aplicar la respuesta localmente |
| Respuestas de errores recuperables | No se marca como entregado | Se reintenta con la misma clave si procede; no se ACKea un error como éxito |
| Notificación push | No es el ACK de los datos | Push solo despierta; la app descarga, guarda y ACKea desde la bandeja durable |

## 3. Sobre de protocolo

Cada operación de escritura del cliente incluye un `operationId` UUID estable generado una sola vez y persistido antes del envío. Reintentos reutilizan el mismo ID y el mismo cuerpo canónico. El servidor almacena el resultado de la operación y devuelve el mismo recibo ante duplicados.

Cada lote de entrega del servidor incluye:
- `deliveryId`: identificador estable del lote.
- `recipientDeviceId` y `recipientRole`: destinatario autenticado y ámbito (VENTAS_1, VENTAS_2, PATRON o ADMIN).
- `cursor`: posición monotónica del inbox o flujo.
- `items[]`: eventos/datos con `eventId`, `version`, `type`, `createdAt` de servidor y `payload`.
- `payloadHash`: SHA-256 del contenido canónico del lote.
- `serverTime`: hora oficial usada para vencimientos y fechas de negocio.

El ACK incluye `ackId` estable, `deliveryId`, `cursor`, `payloadHash`, cantidad de elementos confirmados y el dispositivo autenticado. Para ACK por lote se admite confirmar todos los elementos contiguos hasta el cursor indicado. Si algún elemento no pudo persistirse, el cliente no debe confirmar el cursor que lo incluye; puede confirmar un prefijo completo anterior o solicitar/reintentar el lote.

El ACK viaja firmado con el mismo mecanismo de reto P-256 de un solo uso ya usado por el API. El reto liga método, ruta, propósito y hash exacto del cuerpo. El ACK no debe usar secretos administrativos embebidos en las APK.

## 4. Secuencia de entrega segura

1. El Worker crea o selecciona una entrega durable y conserva su contenido en D1/R2 según el tipo de dato.
2. El cliente descarga la entrega usando cursor o versión. La respuesta incluye identificadores y hash.
3. El cliente valida destinatario, esquema, tamaño, versión y hash.
4. El cliente guarda el lote completo en una transacción local. Para archivos/paquetes grandes, escribe primero a un archivo temporal, valida y luego hace un cambio atómico a estado confirmado.
5. El cliente envía ACK firmado. El ACK puede agrupar varios eventos contiguos.
6. El Worker registra el ACK idempotentemente y solo entonces marca la entrega como recibida/confirmada.
7. La limpieza de payloads se ejecuta después del ACK y conserva un recibo/auditoría compacta.

Si se pierde la respuesta del ACK, el cliente repite exactamente el mismo `ackId`. El servidor responde con el resultado previamente registrado, sin duplicar escrituras ni volver a aplicar la operación.

Si el teléfono se apaga entre los pasos 4 y 5, el cliente conserva el lote y reenvía el mismo ACK al reconectar. Si se apaga antes del paso 4, no existe ACK y el servidor conserva la entrega para reintento.

## 5. Idempotencia y deduplicación

- `operationId` identifica cada comando originado por el cliente.
- `eventId` identifica cada evento durable del servidor.
- `deliveryId` identifica cada paquete de entrega.
- `ackId` identifica cada confirmación.
- Restricciones únicas en D1 deben impedir aplicar dos veces la misma operación, evento o ACK.
- Reutilizar un ID con un cuerpo/hash distinto debe rechazarse como conflicto, no tratarse como reintento válido.
- La deduplicación se consulta antes de consumir cuotas de operaciones. Solo una operación nueva, autenticada, autorizada y aceptada consume cuota; los reintentos/duplicados no.
- La aceptación de un comando y la confirmación de su entrega son estados distintos. No se debe borrar un comando pendiente porque el servidor lo recibió; se conserva el recibo hasta que el cliente confirme que lo guardó.

## 6. Cursor, lotes y ahorro de tráfico

- Cada bandeja usa cursor monotónico por destinatario/flujo, no consultas completas repetidas.
- El cliente solicita solo cambios posteriores al cursor durable que ya aplicó.
- Una consulta combinada puede incluir inbox, decisiones, contadores, configuración versionada y cursor para evitar llamadas separadas.
- El ACK de esa consulta confirma el cursor y todos los eventos contiguos incluidos, una vez guardados.
- Los contadores son una vista derivada del inbox; no se mantienen mediante una segunda fuente duplicada.
- El servidor no usa polling permanente, WebSockets ni long polling. Push FCM opcional solo despierta la app.
- Los ACK pueden agruparse por destinatario y flujo. No retrasar el ACK de un paquete crítico si eso aumenta riesgo de pérdida.
- Los reintentos usan backoff progresivo y conservan IDs/cuerpos originales.
- El servidor usa su hora oficial; el cliente conserva ancla de tiempo servidor + reloj monotónico, sin consultar hora de Internet continuamente.

## 7. Retención y eliminación

- Ningún evento, reporte, decisión, resultado de pago o snapshot se elimina antes de su ACK válido, excepto datos temporales expirados según una política explícita que no afecte una operación pendiente.
- Después del ACK, se puede eliminar el payload pesado cuando ya no sea necesario, dejando recibo compacto, hash, IDs, destinatario, fecha y auditoría.
- Para el paquete de día de pago, PATRÓN debe almacenar el paquete completo de ambos equipos antes de ACK. Después del ACK el Worker puede eliminar los snapshots diarios de origen y reemplazar el JSON grande de entrega por un recibo compacto; no conservar una segunda copia íntegra dentro de `payday_deliveries.payload_json`.
- La limpieza debe ser durable, por lotes, reintentable e idempotente. No borrar primero y registrar después.
- Los comprobantes de pago, si se habilitan en R2, siguen su propio ciclo: validación administrativa, ACK/resultado entregado a la app y eliminación del objeto temporal; la auditoría conserva metadatos y hash, no la imagen.

## 8. Límites por aplicación

### SENE VENTAS
- Persiste localmente ventas, caja, gastos, productos e inventario operativo; no los sincroniza al servidor.
- Cada solicitud, reporte compacto, snapshot diario y comando remoto tiene ID idempotente y recibo.
- Guarda respuestas/decisiones localmente antes de ACK.
- Reintenta colas con los mismos IDs y no crea duplicados.
- El flujo local de reportes cada dos horas y el WhatsApp manual iniciado por el trabajador no requieren ACK de servidor porque no son entregas al servidor.

### SENE PATRÓN
- Descarga en una operación combinada los cambios pendientes de ambos equipos cuando sea posible.
- Guarda inbox, decisiones, reportes y configuración antes de confirmar cursores.
- Contadores se calculan desde el inbox confirmado y permanecen independientes por equipo.
- El paquete de día de pago se guarda íntegro antes del ACK de paquete.

### SENE ADMIN
- Descarga la bandeja administrativa durable por cursor.
- Guarda eventos y resultados localmente antes del ACK.
- Abrir, filtrar o leer un evento no cambia su estado de entrega.
- No recibe datos de ventas, caja, inventario operativo, solicitudes laborales ni nómina.

### Worker/D1
- Es fuente de verdad de entregas, cursores, recibos y ACK.
- Guarda primero la entrega y luego intenta push.
- Reintentos y ACK duplicados son seguros.
- No usa WhatsApp para alertas ni entrega de códigos.
- ADMIN y PATRÓN tienen flujos, permisos y bandejas separados.

## 9. Requisitos de esquema y API

La implementación debe incorporar, usando migraciones nuevas con numeración única (no renumerar migraciones ya aplicadas):
- Tabla de recibos de operaciones idempotentes con clave única por dispositivo/operationId y hash de cuerpo.
- Tabla/estructura de entregas durable con deliveryId, destinatario, cursor, hash, estado y payload o referencia al payload.
- Tabla de elementos/eventos con eventId único, flujo, destinatario, versión y estado de confirmación.
- Tabla de ACK con ackId único, deliveryId/cursor/hash, dispositivo y hora oficial.
- Restricciones/índices para deduplicación, consulta por destinatario/cursor y limpieza por estado/fecha.
- Endpoints firmados para ACK por lote y consulta incremental por cursor.
- Respuestas de escritura con recibo idempotente y estado de persistencia.
- ACK específico para resultados sensibles cuando se requiera confirmar que la APK guardó el resultado.

La migración no debe duplicar ni renombrar las migraciones existentes. Primero inspeccionar el esquema real y las migraciones aplicadas; usar el siguiente número libre en cada repo/Worker.

## 10. Pruebas obligatorias antes de considerar integrado

1. Repetir una operación con el mismo operationId y cuerpo: un solo efecto y mismo recibo.
2. Repetir operationId con cuerpo diferente: conflicto explícito.
3. Repetir ACK con el mismo ackId: éxito idempotente, sin escrituras duplicadas.
4. Cortar Internet después de guardar localmente y antes del ACK: reenvío del mismo ACK al reconectar.
5. Apagar el teléfono antes de guardar el lote: el servidor conserva la entrega.
6. Entrega de 100 eventos: ACK por lote/cursor, sin perder ni duplicar eventos.
7. Dos equipos PATRÓN: ACK de Equipo 1 no confirma Equipo 2.
8. Paquete de pago: no limpiar snapshots hasta ACK; limpiar payload pesado después y conservar recibo compacto.
9. ADMIN apagado: eventos administrativos siguen pendientes y se entregan al reconectar.
10. Notificación push perdida: la consulta incremental recupera los eventos.
11. Reintentos y duplicados no consumen cuota semanal.
12. Datos sensibles no cruzan bandejas entre VENTAS, PATRÓN y ADMIN.
13. Hash alterado, cursor saltado o destinatario incorrecto: no ACK y error auditable.
14. Compilación CI de las tres APK y pruebas Worker/D1 en entorno de pruebas; después pruebas de integración y, finalmente, pruebas físicas en teléfonos reales.

## 11. Orden de implementación

1. Inventariar todos los endpoints, tablas, colas y flujos actuales; identificar ACK ya existente y brechas.
2. Acordar/validar migraciones nuevas y contrato de cursor/entrega/ACK sin tocar producción.
3. Implementar primero el núcleo Worker/D1 idempotente y sus pruebas.
4. Integrar cliente VENTAS y sus colas persistentes.
5. Integrar cliente PATRÓN, inbox combinado, contadores y paquete de pago.
6. Construir SENE ADMIN con autenticación, bandeja, cursor y ACK.
7. Ejecutar pruebas de caída, duplicación, ACK perdido, retención, separación de datos y cuota.
8. Solo cuando el conjunto esté integrado y verificado, preparar APK de prueba para teléfonos reales. No entregar APK parciales.
