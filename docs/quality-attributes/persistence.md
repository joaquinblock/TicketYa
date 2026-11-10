# Persistencia

> **Enunciado:** "Cada microservicio debe tener preferentemente su propia base de datos […]. Al menos un microservicio debe utilizar una base de datos NoSQL […], justificando la elección. Se valorará el uso de otras tecnologías según el tipo de datos (por ej. Redis para reservas temporales o la fila virtual)."

## Cómo lo definimos

Primero se separó cada dato según qué servicio es su dueño. Después se eligió la tecnología por el tipo de acceso: transacciones y restricciones fuertes, documentos flexibles o estructuras en memoria.

## Qué elegimos

| Servicio | Base | Por qué |
|---|---|---|
| auth-service | MySQL 8.4 | Relaciones claras entre usuarios, roles y organizaciones |
| event-service | MySQL 8.4 | Lugares, sectores y eventos están muy relacionados. Es candidato a MongoDB |
| ticket-service | MySQL 8.4 | La no doble venta se apoya en transacciones, `UPDATE` atómicos y restricciones de unicidad |
| payment-service | MySQL 8.4 | Dinero: necesita las mismas garantías |
| notification-service | MySQL 8.4 | Historial simple de envíos |
| queue-service | **Redis 7.4** | Una *sorted set* por evento resuelve posición y tandas |
| recommendation-service | **MongoDB 8.0** | Es la base NoSQL del sistema: las preferencias son datos flexibles por usuario y no necesitan transacciones entre documentos |

Cada servicio tiene su propia base en su propio contenedor. Sin embargo, algunas decisiones cruzan los límites entre servicios:

- **Referencias por ID, sin FK física.** Por ejemplo, `orders.user_id` apunta a un usuario de otra base. El enunciado las llama "entidades solapadas".
- **Copias deliberadas.** `event_sale` y `event_sector_price` copian en ticket-service los datos de venta del evento, para que reservar no dependa de event-service. `payment.amount` conserva lo que realmente se cobró.
- **Restricciones en la base y no solo en el código.** Las garantías importantes son `UNIQUE`, `CHECK` y columnas generadas.
- **Esquema y datos de prueba.** En desarrollo se usa `ddl-auto: create` con un seeder por servicio, y cada seeder omite la carga si ya hay datos.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Una base compartida | Acopla los servicios por sus tablas internas |
| Redis para reservar lugares | Suma otra tecnología al núcleo de la compra, cuando la base relacional ya garantiza la consistencia |
| MongoDB para ticket-service | Lo que hace falta es justamente lo que da la base relacional |

## Estado

- **Implementado:** las bases MySQL de auth, event, ticket y payment, con sus modelos y seeders.
- **Pendiente (Entrega 2):** los modelos de queue-service en Redis, de recommendation-service en MongoDB y de notification-service. Las bases y las dependencias ya están configuradas.
- **Abierto:** si event-service pasa a MongoDB.

## Dónde verlo

- [`ticket-service.md`](../architecture/ticket-service.md), [`payment-service.md`](../architecture/payment-service.md) y [`auth-service.md`](../architecture/auth-service.md) detallan cada modelo.
- [RFC-004](../rfc/RFC-004-cross-service-relations.md) trata las relaciones entre servicios.
