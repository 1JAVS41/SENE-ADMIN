# Reconstrucción maestra del ecosistema SENE
Fecha de auditoría: 2026-11-12

## 1. Propósito y criterio de reconstrucción

Este documento consolida los acuerdos funcionales del proyecto y los contrasta con el código que existe en las ramas de los tres repositorios. No considera que una función esté terminada solo porque exista un botón, una ruta API o una compilación exitosa.

La regla de entrega es estricta: no se entregarán APK de prueba hasta que SENE VENTAS, SENE PATRÓN, SENE ADMIN y el backend estén integrados y verificados como un conjunto listo para comenzar pruebas en teléfonos reales. Una compilación CI no equivale a integración completa ni a prueba física.

La reconstrucción debe conservar mejoras reales, corregir divergencias y eliminar código obsoleto solo después de comprobar que no contiene una función única necesaria. No se deben borrar ramas ni reescribir historiales antes de rescatar sus cambios útiles.

## 2. Auditoría de ramas y fuente de verdad

### SENE VENTAS — repositorio 1JAVS41/SENE-VENTAS

Ramas encontradas:
- `integration/ventas-completa`: rama principal de integración y versión más reciente revisada. HEAD `653cc47a`.
- `fix/ventas-cloudflare-client`: rama divergente con mejoras importantes de identidad, restauración y correcciones. HEAD `798f49a3`.
- `main`: copia anterior, atrasada respecto de la integración.

Decisión:
- Mantener `integration/ventas-completa` como base de integración.
- No reemplazarla por `main`.
- No descartar `fix/ventas-cloudflare-client` sin rescatar sus mejoras.

Mejoras del branch fix que deben incorporarse o verificarse en la integración:
1. Identidad lógica de instalación determinista para el mismo teléfono, basada en el identificador de continuidad y el paquete.
2. Flujo visible y firmado para restaurar SENE VENTAS después de reinstalar en el mismo teléfono.
3. Recuperación del historial remoto de solicitudes cuando se reinstala la aplicación.
4. Guardado del slot asignado y de los datos de sesión después de vincular.
5. Reintentos de correcciones pendientes sin duplicar solicitudes.
6. Persistencia de borradores de corrección y aplicación local reanudable.

La integración actual ya contiene mejoras importantes procedentes de esa línea de trabajo o reimplementadas posteriormente: PIN del patrón fijo 2910, cola de reportes con reintentos, corrección local transaccional, plan de corrección con hash, validaciones de tickets e inventario, QR SENEPAIR2, reportes resumidos y snapshots diarios de inventario.

Brechas comprobadas en la rama de integración:
- El cliente actual `SeneApiClient` no expone el método `restoreSamePhone`, aunque el Worker ya tiene `POST /v1/ventas/restore`.
- `DeviceIdentity.kt` crea un `installationId` aleatorio guardado en preferencias. Tras desinstalar, esas preferencias pueden desaparecer. La rama fix contiene la variante determinista que debe revisarse e integrarse.
- El cliente actual no expone una descarga inicial completa del historial remoto, aunque el Worker dispone de `/v1/ventas/requests/history`.
- La fecha del snapshot diario se obtiene con el reloj del teléfono; debe derivarse de la hora oficial del servidor para evitar fechas incorrectas.
- `Protection.kt` conserva una función antigua `addBlindButton` que ya no se invoca. El blindaje debe ser automático y transparente, sin volver a mostrar el botón BLINDAR APK.

### SENE PATRÓN — repositorio 1JAVS41/SENE-PATRON

Ramas encontradas:
- `sene-cloudflare-backend`: base actual de integración. HEAD `c89b8822`.
- `sene-patron-development`: rama anterior cuyo trabajo fue continuado en la rama de backend.
- `fix/patron-gradle-compat` y `fix/patron-gradle-toolchain`: ramas de corrección de Gradle/toolchain y de etapas anteriores de recuperación.
- `main`: rama antigua y atrasada.

Decisión:
- Mantener `sene-cloudflare-backend` como fuente de verdad para PATRÓN y el backend.
- Las ramas de desarrollo anteriores sirven como historial de auditoría; no se deben mezclar a ciegas.
- Las correcciones de recuperación, vinculación, pagos, reemplazo, identidad y pruebas que ya están presentes en la rama actual se conservan desde la rama actual.
- Los ajustes antiguos de Kotlin/Gradle 2.0.21 no deben reemplazar la configuración más reciente, que ya compila en CI con el toolchain actual.

Hallazgos estructurales:
- El proyecto Android activo se compila desde `src/main` en la raíz del repositorio. El archivo `settings.gradle.kts` no incluye un módulo `:app`; por tanto, el árbol `app/src` es una copia antigua/no activa y debe compararse antes de retirarse para evitar mantener dos implementaciones confundibles.
- La interfaz actual usa acentos rojo/amarillo y un icono separado. Debe alinearse con el diseño azul/verde y el logotipo/icono original acordados para SENE VENTAS.
- El encabezado sí carga el recurso `logo_sene_pv`, pero el nombre de la función `redLogo` y el icono de instalación requieren limpieza y verificación visual.
- El texto informativo de la pantalla todavía dice que no se sincronizan inventarios, mientras que el backend y VENTAS ya implementan snapshots diarios temporales. Debe corregirse para explicar la excepción aprobada.

### SENE ADMIN — repositorio 1JAVS41/SENE-ADMIN

- Solo existe la rama `main`, con el README inicial.
- No existe todavía proyecto Android, APK, autenticación administrativa, bandeja de eventos ni endpoints administrativos operativos.
- Los documentos iniciales de SENE ADMIN están dentro del repositorio SENE-PATRON, en `sene-admin/docs`; deben trasladarse y mantenerse como documentación del repositorio SENE-ADMIN.
- SENE ADMIN debe construirse como aplicación real, no presentarse como terminada por tener README o contrato de API.

## 3. Arquitectura general y separación de responsabilidades

Una empresa tiene exactamente tres espacios:
1. Un SENE PATRÓN.
2. Un SENE VENTAS Equipo 1 (`VENTAS_1`).
3. Un SENE VENTAS Equipo 2 (`VENTAS_2`).

Las tres aplicaciones usan un backend central Cloudflare Workers + D1. Las APK no acceden directamente a D1 ni contienen secretos administrativos.

### SENE VENTAS
Es la aplicación del trabajador y administra localmente ventas, caja, gastos, productos e inventario. También gestiona solicitudes laborales, reportes, vinculación, identidad y las autorizaciones de corrección.

### SENE PATRÓN
Es la aplicación del titular de la tienda. Administra los dos equipos por separado, recibe reportes e inventarios temporales, configura nómina y calendario de pago, revisa solicitudes y autoriza correcciones puntuales.

### SENE ADMIN
Es la aplicación del administrador central de SENE. Administra empresas, licencias, reservas de nombres, pagos, renovaciones, reposiciones, recuperaciones, vencimientos, eventos administrativos y auditoría. No recibe ventas, caja, inventario operativo, clientes ni nómina de las tiendas.

## 4. SENE VENTAS — funcionamiento requerido

### 4.1 Primera instalación y acceso local
- Nombre instalado: SENE-VENTAS.
- Idioma: español; orientación vertical.
- Logotipo e icono oficiales.
- La clave maestra local sirve únicamente para el primer acceso; no es una licencia ni autentica contra el servidor.
- El blindaje del dispositivo debe ser automático y transparente mediante Android Keystore.
- El PIN de «Soy Patrón» permanece fijo en 2910. No convertirlo en un PIN configurable ni sustituirlo por otro mecanismo.
- El número local del patrón puede conservarse para el envío manual de reportes desde el teléfono; no debe utilizarse para que el servidor envíe alertas.

### 4.2 Vinculación e identidad
- Identidad criptográfica P-256 en Android Keystore; la clave privada no sale del dispositivo.
- El servidor emite retos de un solo uso, limitados por propósito, método, ruta y hash exacto del cuerpo.
- SENE VENTAS firma el reto y el Worker valida la firma, el equipo, la licencia y el slot.
- El QR pertenece exclusivamente al Equipo 1 o Equipo 2, es de un solo uso y no crea un espacio adicional.
- Reinstalar en el mismo teléfono debe permitir recuperar la identidad lógica y la vinculación existente sin consumir otra licencia.
- Si el teléfono se perdió, se usa el proceso de reposición autorizado; no se debe simular una restauración del mismo teléfono.

### 4.3 Operación de tienda
- Ventas, caja, gastos y datos completos de productos permanecen localmente.
- Escáner vertical, activo hasta Atrás; sonido fuerte al detectar y flash solo al detectar según el comportamiento acordado.
- Al salir del escáner, conservar la lista de productos escaneados.
- Producto desconocido: mostrar «NO REGISTRADO» y permitir registrar solo ese producto nuevo.
- El trabajador puede agregar mercancía de proveedor, pero no borrar productos.
- La caja distingue caja inicial, ventas, gastos y efectivo disponible.
- La aplicación sigue permitiendo operar localmente aunque no haya Internet o el servidor esté temporalmente fuera de servicio.

### 4.4 Reportes y jornada
- El aviso de reporte cada dos horas bloquea la continuación hasta que el trabajador confirme el envío del resumen local.
- El TXT completo se guarda en la carpeta INVENTARIO SENE, organizada por fecha.
- El reporte periódico de dos horas es un flujo local; no debe convertirse en una consulta constante al servidor.
- El cierre de turno no se permite antes de cumplir ocho horas.
- Al cerrar turno se genera un resumen compacto, se guarda localmente y se encola para el servidor. La cola es persistente, reintentable e idempotente.
- El reporte enviado al servidor no contiene inventario completo ni detalle de cada venta.
- El envío manual de texto desde el teléfono a WhatsApp puede mantenerse como función local acordada. Esto no es WhatsApp del servidor y no autoriza al Worker a enviar mensajes.

### 4.5 Solicitudes y correcciones
- Las solicitudes de permisos, préstamos, consumos, incidencias y demás tipos aprobados se envían al crearse, no hasta el cierre del turno.
- Cada solicitud tiene UUID/idempotency key y se relaciona con empresa, equipo y dispositivo.
- El trabajador consulta historial y respuestas sin perderlas al cerrar la app.
- Para corregir una venta, se crea un plan local, se calcula su hash y se envían al patrón solo los datos mínimos necesarios para autorizar.
- La autorización se vincula a una venta, solicitud, licencia y dispositivo; es de un solo uso.
- La aplicación debe guardar el plan antes de solicitar autorización, reanudar operaciones interrumpidas y no duplicar la solicitud.
- La aplicación local aplica la corrección de forma transaccional, valida ticket, existencias, importes y caja, y registra un journal durable.
- El servidor nunca recibe el historial completo de ventas para autorizar una corrección.

### 4.6 Día de pago y SENE IA
- El menú Día de pago permite consultar historial de permisos, préstamos, consumos, estados y respuestas.
- El envío de reporte de nómina solo se habilita cuando el servidor confirma la fecha configurada por PATRÓN.
- El reporte de nómina es compacto; no incluye ventas, caja ni inventario.
- SENE IA utiliza su base local aprobada, con las preguntas y variantes acordadas. Las consultas externas solo ocurren cuando el trabajador las solicita expresamente.

## 5. SENE PATRÓN — funcionamiento requerido

### 5.1 Identidad e inicio
- Nombre instalado: SENE PATRÓN.
- Mismo estilo azul/verde y mismo logotipo/icono oficial que SENE VENTAS.
- La clave maestra local es solo para el primer acceso; no es la licencia.
- Blindaje automático y transparente con Android Keystore.
- Conexión de pruebas configurada por la aplicación; el usuario no debe tener que introducir manualmente una URL.

### 5.2 Equipos
- Pantallas independientes Equipo 1 y Equipo 2.
- Cada equipo muestra su identidad, estado de vinculación, trabajador, reportes y solicitudes.
- Los contadores son independientes: rojo con cero pendientes; verde con el número exacto cuando hay una o más.
- El contador representa solicitudes/correcciones pendientes reales; abrir la bandeja no elimina la solicitud ni su historial.
- El QR se genera para el slot seleccionado, se recupera si la pantalla se cierra y no se reutiliza para otro equipo.

### 5.3 Reportes, inventarios y nómina
- Recibe reportes resumidos de cierre por equipo.
- Recibe inventarios diarios temporales del Equipo 1 y Equipo 2 para preparar el paquete del día de pago.
- El servidor conserva las versiones necesarias de inventario durante la semana y selecciona la más reciente de cada equipo que corresponda a la fecha de pago.
- SENE PATRÓN debe guardar el paquete completo localmente antes de enviar ACK.
- El servidor no elimina la información antes del ACK confirmado.
- El ACK debe ser idempotente.
- La nómina se calcula en PATRÓN, separada por trabajador/equipo, considerando sueldo, fechas de pago, permisos, préstamos, consumos y descuentos autorizados.
- Un concepto pendiente o rechazado no se descuenta.
- Los descuentos por faltas/retardos requieren autorización expresa; préstamos y consumos solo se descuentan si el patrón autorizó el importe.

### 5.4 Solicitudes y correcciones
- La bandeja de solicitudes debe combinar solicitudes operativas y correcciones cuando sea posible para reducir consultas.
- El patrón aprueba o rechaza, con detalle de decisión y descuento cuando proceda.
- Las respuestas se devuelven únicamente al dispositivo correcto.
- Las correcciones se autorizan por evento, nunca como permiso general.

### 5.5 Licencias y recuperación
- Alta inicial: $580 MXN ($500 alta + $80 primera mensualidad), vigencia inicial de 30 días.
- Renovación: $80 MXN por 30 días.
- Reserva del nombre: 48 horas según hora oficial del servidor.
- Si el cliente informa que pagó dentro del plazo, la solicitud debe pasar a «Pendiente de confirmación» y conservarse aunque el administrador confirme después de las 48 horas.
- La reposición de SENE PATRÓN perdido cuesta $500 MXN, genera una sola solicitud/código y reutiliza la misma solicitud en consultas repetidas.
- La reposición no crea una licencia ni un slot nuevo.
- La recuperación por reinstalación en el mismo teléfono no cobra y debe conservar la misma empresa/slot.
- Si se pierde la llave o se daña el QR, se reutiliza el código original ya pagado y validado; no se cobra ni se genera una autorización comercial nueva.
- La renovación, recuperación y reposición se validan en el servidor.

## 6. SENE ADMIN — funcionamiento requerido

SENE ADMIN será una aplicación Android nativa en español, con la misma identidad visual de SENE y un panel administrativo claro.

Módulos:
1. Inicio y métricas.
2. Empresas y licencias.
3. Altas y reservas de nombre.
4. Pagos pendientes y validación manual.
5. Renovaciones.
6. Reposiciones y recuperaciones.
7. Dispositivos y los tres slots.
8. Licencias próximas a vencer y vencidas.
9. Bandeja persistente de eventos administrativos.
10. Auditoría y diagnóstico.

### 6.1 Seguridad administrativa
- Cuentas individuales, sesiones cortas y revocables.
- MFA para permisos sensibles.
- Roles y permisos mínimos: super_admin, payments_reviewer, support_readonly y auditor_readonly.
- Nunca incluir ADMIN_TOKEN, OPERATOR_TOKEN ni credenciales del Worker en la APK.
- El administrador no accede directamente a D1 o R2; todo pasa por el Worker.
- Toda acción registra actor, acción, entidad, hora oficial y resultado.

### 6.2 Pagos
- SENE PATRÓN muestra al cliente el folio único antes de pagar.
- El concepto/referencia de transferencia debe contener exactamente ese folio.
- El mismo folio identifica la solicitud y aparece en SENE ADMIN.
- El administrador verifica el movimiento bancario real.
- Una captura o comprobante nunca confirma automáticamente el pago.
- Al confirmar, el Worker registra importe, referencia bancaria, fecha, actor y decisión en una operación idempotente.
- Tras la confirmación, el código/autorización se entrega a la aplicación PATRÓN mediante un canal seguro dentro del sistema; no se dependerá de WhatsApp para entregar códigos.
- El comprobante puede almacenarse temporalmente en R2 privado. D1 conserva solo metadatos. Después de confirmar, el objeto se elimina con una tarea durable reintentable y se conserva la auditoría, no la imagen.

### 6.3 Notificaciones administrativas
- SENE ADMIN recibe licencias vencidas/próximas a vencer, altas, pagos, renovaciones, reposiciones, recuperaciones y otros eventos administrativos.
- No recibe inventarios, ventas, caja, solicitudes laborales ni nómina de las tiendas.
- El servidor guarda cada evento en una bandeja durable antes de intentar notificar.
- Push solo despierta la aplicación; el contenido y estado real se consultan en el servidor.
- La aplicación descarga el evento y confirma recepción con ACK.
- Si el teléfono está apagado o sin Internet, el evento permanece pendiente.
- No habrá alertas administrativas enviadas por WhatsApp desde el servidor.

## 7. Backend central

### 7.1 Tecnología y entornos
- Cloudflare Worker para API y lógica de negocio.
- Cloudflare D1 para licencias, dispositivos, solicitudes, pagos, eventos, auditoría y metadatos.
- R2 privado para comprobantes temporales, solo cuando el flujo de evidencia esté habilitado.
- Entorno de pruebas aislado: Worker `sene-licensing-api-testing` y D1 `sene-licencias-pruebas`.
- Producción permanece separada. No desplegar ni aplicar migraciones a producción sin autorización explícita.
- Firebase Firestore/Functions/Blaze no forma parte del backend central. FCM podría utilizarse exclusivamente como aviso push de bajo contenido si se configura de forma segura; no sustituye D1 ni la bandeja durable.

### 7.2 Identidad y autorización
- Cada dispositivo genera clave P-256 en Android Keystore.
- El Worker emite reto aleatorio de un solo uso, de vida corta, ligado a propósito, método, ruta y hash del cuerpo.
- El cliente firma el mensaje exacto.
- El Worker verifica firma, licencia, estado del equipo, slot y permisos.
- Los códigos de activación/renovación/reposición son de un solo uso y se guardan como hash; los códigos que necesiten recuperarse por una respuesta perdida se conservan cifrados en D1.
- Los secretos de operador y de cifrado viven solo como secretos del Worker.

### 7.3 Datos que conserva el servidor
- Licencias, estado, vigencia, pagos y folios.
- Identidad de instalaciones y dispositivos, slots y revocaciones.
- Retos criptográficos temporales.
- Solicitudes y decisiones laborales mínimas.
- Correcciones y grants de un solo uso.
- Reportes resumidos de turno y nómina.
- Snapshots diarios temporales de inventario, como excepción controlada para día de pago.
- Bandeja administrativa, ACK y auditoría.
- No conserva el detalle completo de cada venta, clientes, caja ni gastos comerciales.

### 7.4 Eliminación de dependencia de WhatsApp del servidor
El Worker actual todavía contiene dependencias heredadas de números AYUDA: `deliveryPhone`, `requireOfficialHelpRecipient`, `official_support_contacts`, instrucciones de enviar comprobantes por WhatsApp y el panel HTML `/operator/replacements` que entrega códigos manualmente. Esos elementos deben sustituirse por SENE ADMIN, bandeja administrativa, carga temporal de comprobantes y entrega segura de códigos a SENE PATRÓN.

Esto no obliga a eliminar las acciones manuales de WhatsApp que viven dentro de SENE VENTAS y que el trabajador inicia desde su teléfono para compartir su reporte local. La exclusión es la lógica/dependencia del servidor, no una autorización para borrar funciones locales previamente acordadas.

## 8. Plan de ahorro de tráfico y almacenamiento

### 8.1 Objetivo
Dimensionamiento inicial acordado: 200 empresas y hasta 600 dispositivos (1 PATRÓN + 2 VENTAS por empresa). Se mantiene el objetivo de Cloudflare Workers Free + D1 Free, sin Firebase Blaze ni conexiones permanentes.

Las cuotas de Cloudflare que se utilizaron en la planificación inicial fueron 100.000 solicitudes Worker/día, 5 millones de filas D1 leídas/día, 100.000 filas D1 escritas/día y 5 GB de almacenamiento D1. Deben volver a verificarse en la cuenta y plan vigentes antes de producción; el diseño no debe depender de excederlas.

### 8.2 Límite de operaciones SENE
El Worker actual implementa un máximo atómico de 20 operaciones remotas autenticadas por dispositivo y semana ISO de America/Mexico_City. La operación número 21 se rechaza. La sesión de dispositivo no consume esa cuota.

Para 600 dispositivos:
- Máximo teórico: 12.000 operaciones de negocio por semana.
- El protocolo actual usa un reto y una llamada de negocio por operación; como aproximación son hasta 24.000 solicitudes Worker por semana, unas 3.429 al día de promedio, antes de sesiones, administración y reintentos.
- La cuota debe aplicarse a operaciones únicas aceptadas, no a cada reintento de una operación ya procesada.

Problema encontrado: actualmente `signedDeviceContext` consume cuota antes de validar todo el cuerpo y antes de comprobar si una operación idempotente ya existe. Un reintento, duplicado o error posterior puede gastar cuota otra vez. Debe corregirse con una clave de operación única y consumo de cuota atómico junto con la operación aceptada.

### 8.3 Qué se sincroniza y cuándo
- Ventas detalladas: nunca.
- Caja y movimientos completos: nunca.
- Gastos comerciales detallados: nunca.
- Inventario: no existe sincronización continua. Se permite únicamente el snapshot diario temporal aprobado para día de pago.
- Snapshot diario: máximo uno por fecha y equipo si el contenido cambió; hash estable para no reenviar solo por cambiar la hora de captura; tamaño máximo actual 32 KB.
- Reporte de cierre: solo al cerrar turno, resumen compacto e idempotente.
- Reporte periódico de dos horas: local; no consume cuota del servidor.
- Solicitudes laborales: al crearse, con idempotency key.
- Respuestas: delta desde el último cursor; no descargar historial completo en cada consulta.
- Nómina: solo en la fecha que el servidor confirma.
- Día de pago: una solicitud de paquete por fecha, con reintento idempotente y ACK.
- Licencia/sesión: consultar al iniciar o cuando venza la caché, no al abrir cada pantalla.
- Bandejas: una consulta combinada para solicitudes, correcciones y contadores por equipo; evitar llamadas separadas redundantes.
- Reportes: descargar bajo demanda o por cursor incremental; no consultar cada hora sin necesidad.
- Administración: push de aviso y sincronización del inbox solo al abrir, al recibir push, al recuperar conexión o por actualización manual; no polling frecuente.

### 8.4 Inventario y ACK
Flujo:
1. Cada VENTAS envía un snapshot diario con empresa/equipo/fecha.
2. D1 conserva solo las versiones necesarias de la última semana.
3. En el día de pago el Worker selecciona el snapshot más reciente de cada equipo que tenga pago ese día.
4. El Worker prepara un paquete con inventario seleccionado y reportes de nómina.
5. PATRÓN descarga y guarda todo localmente.
6. PATRÓN confirma ACK.
7. Solo después del ACK se eliminan los snapshots y copias completas del paquete que ya no se necesitan.

Problema encontrado: el ACK actual borra filas de `daily_inventory_snapshots`, pero deja el JSON completo dentro de `payday_deliveries.payload_json`. Por tanto, el inventario sigue almacenado en D1. Debe reemplazarse el payload por un recibo compacto (IDs, fechas, hashes, equipos, estado y fecha de ACK) después de confirmar la recepción. También falta una limpieza de snapshots antiguos que nunca entraron en un paquete confirmado.

### 8.5 Consultas combinadas y caché
- Un endpoint de sincronización PATRÓN debe devolver en una sola respuesta contadores, solicitudes/correcciones nuevas y cursores; detalles de reportes grandes se descargan solo al abrirlos.
- VENTAS debe agrupar respuestas e historial en deltas.
- La sesión y hora del servidor se guardan con un ancla monotónica; no se solicita la hora a Internet continuamente.
- Evitar listeners permanentes, WebSockets, long polling y bucles de red.
- Usar backoff progresivo para errores de conexión y detener reintentos cuando la licencia o cuota lo indiquen.
- El teléfono conserva operaciones locales; si falla Internet, no se pierden ventas ni reportes pendientes.

### 8.6 Medición de consumo
El servidor debe medir por dispositivo y licencia:
- operaciones usadas/restantes por semana;
- bytes recibidos y enviados;
- endpoint y tipo de operación;
- errores, reintentos y duplicados;
- lecturas/escrituras D1 aproximadas por flujo;
- cantidad/tamaño de snapshots y reportes retenidos;
- tamaño y antigüedad de bandejas pendientes.

Los contadores deben agregarse en registros semanales existentes cuando sea posible, evitando crear una fila nueva por cada evento. La app debe mostrar consumo y cuota restante en el panel técnico/diagnóstico. Alertas internas sugeridas al 70%, 85% y 100% de uso.

### 8.7 Limpieza de datos
- Retos consumidos/expirados: eliminar periódicamente los registros antiguos; no acumularlos indefinidamente.
- Snapshots: retención de la semana necesaria y eliminación tras ACK.
- Paquetes de día de pago: conservar recibo compacto después de ACK, no el inventario completo.
- Reportes de turno/nómina: implementar cursor y ACK/retención para no reenviar siempre los últimos 50; conservar el historial necesario en PATRÓN y una política explícita en servidor.
- Tokens QR consumidos/revocados: borrar el token cifrado cuando ya no sea recuperable y conservar solo metadatos de auditoría.
- Métricas de cuota: conservar ventanas recientes y resumir las antiguas.
- Auditoría: conservar metadatos, nunca secretos, comprobantes ni datos de venta.

## 9. Hallazgos técnicos que bloquean la reconstrucción final

1. SENE VENTAS no tiene aún integrado el flujo Android de restauración en el mismo teléfono, aunque el Worker ya tiene la ruta.
2. SENE VENTAS no recupera el historial remoto completo al reinstalar.
3. El identificador lógico de instalación de VENTAS aún es aleatorio y debe alinearse con la continuidad estable.
4. El Worker todavía depende de números oficiales AYUDA para autorizar/entregar códigos y conserva un panel HTML heredado. Debe migrarse a SENE ADMIN y entrega dentro de la app.
5. No existen endpoints de sesión administrativa, roles, bandeja durable, eventos ni ACK de SENE ADMIN.
6. El caso de pago reportado dentro de las 48 horas y confirmado por el administrador después del vencimiento no está cerrado en el Worker; se necesita estado persistente `pending_confirmation`.
7. El ACK del paquete de pago no elimina el inventario duplicado que queda dentro de `payday_deliveries.payload_json`.
8. Los snapshots antiguos no tienen limpieza completa por antigüedad.
9. Las consultas de reportes PATRÓN pueden repetirse cada hora y devuelven hasta 50 reportes sin cursor/ACK.
10. `patronNotifications` y `patronRequestsInbox` duplican parte de la información de contadores; debe quedar una sincronización combinada.
11. La cuota se consume antes de validar y deduplicar completamente la operación; los reintentos pueden gastar cuota.
12. Los retos criptográficos y los registros antiguos de uso requieren política de limpieza.
13. SENE ADMIN no tiene aplicación Android ni backend funcional.
14. La evidencia de pago en R2, sus URL temporales y su cola de eliminación solo están documentadas; no están implementadas.
15. El diseño visual de PATRÓN debe volver a azul/verde y usar icono/logotipo oficiales.
16. Hay numeración repetida en algunos nombres de migración 0011, 0012 y 0013. No renombrar migraciones ya aplicadas; las nuevas deben continuar con números únicos a partir de 0017.
17. Las pruebas actuales de CI no equivalen a una prueba E2E de las tres APK en teléfonos reales.

## 10. Orden de reconstrucción

### Fase A — congelar fuentes de verdad
- VENTAS: `integration/ventas-completa`.
- PATRÓN/backend: `sene-cloudflare-backend`.
- ADMIN: `main` como repositorio documental inicial, todavía sin app.
- Mantener las ramas antiguas como referencia hasta rescatar sus mejoras; no borrar ni forzar historial.

### Fase B — cerrar VENTAS
- Incorporar identidad determinista y restauración en el mismo teléfono desde el branch fix.
- Integrar recuperación del historial.
- Usar hora oficial para fecha de inventario.
- Mantener PIN 2910, clave maestra local, blindaje automático, corrección transaccional, reportes y operación offline.
- Retirar código muerto del botón BLINDAR APK.

### Fase C — cerrar PATRÓN y optimización
- Unificar sync de solicitudes/correcciones/contadores.
- Añadir cursor y ACK para reportes.
- Corregir consumo idempotente de cuota.
- Corregir limpieza de inventario y payload del paquete tras ACK.
- Implementar estado de pago reportado dentro de 48 horas.
- Alinear interfaz e icono con SENE VENTAS.

### Fase D — backend administrativo sin WhatsApp
- Implementar sesiones y roles administrativos.
- Crear bandeja durable y eventos idempotentes.
- Generar eventos al crear pagos, renovar, reponer, recuperar y vencer licencias.
- Entregar códigos a PATRÓN por canal seguro interno.
- Implementar comprobantes R2 privados y borrado reintentable, si se habilita la captura.
- Retirar la dependencia de deliveryPhone/AYUDA/WhatsApp para operaciones del servidor.
- No modificar Worker ni D1 productivos.

### Fase E — SENE ADMIN
- Crear proyecto Android nativo con identidad SENE.
- Implementar login/MFA, dashboard, bandejas, pagos, licencias, equipos, reposiciones, auditoría y sincronización.
- Conectar solo al backend aislado de pruebas.

### Fase F — integración y aceptación
- Probar flujos completos entre las tres APK y Worker/D1 de pruebas.
- Probar desconexión, reintentos, ACK, concurrencia, idempotencia, expiración y recuperación.
- Ejecutar CI de las tres APK y del backend.
- Verificar que los APK correspondan a las ramas/commits integrados.
- Solo entonces preparar versiones para iniciar pruebas en teléfonos reales.
- No afirmar que un teléfono real fue probado hasta realizar esa prueba física.

## 11. Estado de la auditoría

Esta reconstrucción se basa en la lectura de las ramas y archivos principales disponibles al 2026-11-12. Se identificaron las ramas de referencia, mejoras rescatables, duplicados, brechas de integración y problemas de tráfico/retención descritos arriba.

Este documento es la especificación maestra para reconstruir el sistema. No afirma que los pendientes ya estén implementados. Los repositorios y el entorno de pruebas deben seguir separados de producción hasta completar la integración y las verificaciones correspondientes.
