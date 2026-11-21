# notification-service: mail de la compra

> US-NOT-01. Es un MVP: hoy el servicio arma y envía el mail de una compra confirmada, pero todavía nada lo dispara. El aviso llega después por RabbitMQ (`purchase.confirmed`, US-TKT-24).

## Cómo se arma el mail

`PurchaseMailService.send(orderId, userId)` hace tres pasos:

1. Pide la orden a `GET /internal/orders/{id}` del ticket-service: el evento (nombre, fecha, lugar) y las entradas con su ubicación y su `qrCode`. Solo responde órdenes `PAID`.
2. Pide el email y el nombre a `GET /internal/users/{id}` del auth-service.
3. `PurchaseMailComposer` arma un HTML simple, sin motor de plantillas, y se envía con `JavaMailSender`.

## El QR en el mail

`QrCodeGenerator` convierte el `qrCode` de cada entrada (un UUID) en un PNG de 240 × 240 con el núcleo de ZXing (`com.google.zxing:core`). La imagen se arma con `ImageIO`, así no hace falta el módulo `javase` de ZXing.

- **Va como imagen embebida**, no como `data:` en el HTML. El HTML la referencia con `<img src="cid:qr-{ticketId}">` y el mail lleva una parte `image/png` por entrada. Gmail y otros clientes bloquean las imágenes `data:`.
- **Debajo va el código en texto**, por si el cliente de mail no muestra imágenes o el lector del ingreso falla.
- Una entrada sin `qrCode` (por ejemplo, reintegrada) muestra "No disponible" y no lleva imagen.

Los dos clientes mandan el secreto interno solos (`common`, US-PAY-14) y tienen timeouts de 1 s para conectar y 2 s para leer.

## Qué pasa si algo falla

| Caso | Qué hace | Por qué |
|---|---|---|
| La orden no existe (404) o no está pagada (409) | No envía, deja un `WARN` y termina sin error | No hay compra que avisar: reintentar no cambia nada |
| El usuario no existe o está dado de baja (404) | Igual: no envía y deja un `WARN` | No hay a quién avisar |
| Un servicio responde 5xx o no responde a tiempo | Propaga la excepción | Es momentáneo: quien lo llame puede reintentar |
| Falla el envío por SMTP | Propaga la excepción (`MailException`) | Igual que el anterior |

En el log van el id de la orden y el del usuario, nunca el email ni el QR.

## Configuración

| Propiedad | Variable | Por defecto |
|---|---|---|
| `spring.mail.host` / `spring.mail.port` | `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` |
| `ticketya.mail.from` | `MAIL_FROM` | `no-reply@ticketya.com` |
| `spring.http.serviceclient.ticket-service.base-url` | `TICKET_SERVICE_URL` | `http://localhost:8083` |
| `spring.http.serviceclient.auth-service.base-url` | `AUTH_SERVICE_URL` | `http://localhost:8081` |

En desarrollo, el `compose.yaml` del servicio levanta **Mailpit**: recibe los mails sin enviarlos a nadie y los muestra en `http://localhost:8025`.

## Pendiente

- Escuchar `purchase.confirmed` en RabbitMQ y descartar los mensajes repetidos por `messageId` (US-NOT-01, US-TKT-24).
- Los avisos de cancelación y cambios de un evento (US-NOT-02) y de artistas favoritos (US-NOT-03).
