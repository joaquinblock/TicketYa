Se decidió hacer una máquina de estados para controlar los cambios de estado que puedan provocar problemas en el sistema.

La lista de transiciones de estado de un evento es:

| Desde                   | Hacia                                             |
| ----------------------- | ------------------------------------------------- |
| `DRAFT`                 | `PUBLISHED`                                       |
| `PUBLISHED`             | `SOLD_OUT`, `CANCELLED`, `SUSPENDED`, `FINISHED`  |
| `SOLD_OUT`              | `PUBLISHED`, `CANCELLED`, `SUSPENDED`, `FINISHED` |
| `SUSPENDED`             | `PUBLISHED`, `CANCELLED`                          |
| `CANCELLED`, `FINISHED` | ninguno (estados finales)                         |

## Explicación cambio de estados
- `DRAFT -> PUBLISHED`: Se pasa de borrador a publicado para comenzar a vender las entradas.
- `PUBLISHED -> SOLD_OUT`: Está publicado y se vendieron todas las entradas.
- `PUBLISHED -> CANCELLED`: El evento se canceló definitivamente.
- `PUBLISHED -> SUSPENDED`: El evento se suspendió por alguna cuestión externa (baja demanda, cuestiones climatológicas, etc.).
- `PUBLISHED -> FINISHED`: El evento se realizó con éxito.
- `SOLD_OUT -> PUBLISHED`: Estaba agotado pero una o más personas pidieron su devolución y se liberaron cupos.
- `SOLD_OUT -> CANCELLED`: El evento se canceló definitivamente. Hay que hacer la devolución de pagos a todos los compradores y avisar vía mail.
- `SOLD_OUT -> SUSPENDED`: Se suspendió temporalmente un evento con entradas agotadas.
- `SOLD_OUT -> FINISHED`: Se concretó con éxito el evento cuyas entradas estaban agotadas.
- `SUSPENDED -> PUBLISHED`: Se suspendió en su momento pero se re-programó y volvió a estar publicado. Hay que hacer devolución al cliente.
- `SUSPENDED -> CANCELLED`: Se suspendió temporalmente y se canceló definitivamente

## Estados finales
- `CANCELLED` es estado final porque el evento quedó descartado definitivamente y no va a concretarse.
- `FINISHED` es estado final porque el evento ya se realizó

## Excepciones
Si intenta pasar de `null` a un estado o de un estado a `null` arroja excepción.

### Chequeo de mismo estado
Si se intenta pasar de un estado a el mismo, ejemplo `PUBLISHED` a `PUBLISHED`, la máquina simplemente devuelve ese mismo estado como `next`. Es decir, devuelve `PUBLISHED`.

## Agregar un estado nuevo
Es necesario agregarlo en el `HashMap` junto con los estados a los que puede pasar.

---
## A considerar
- Se puede saltear la máquina. `Event` tiene `@Setter` en toda la clase, así que cualquiera puede hacer `event.setStatus(...)` sin pasar por `transition`, y por ahora nadie la llama. Para que se respete, convendría quitar el setter de status y exponer un método como `event.changeStatus(next)` que la use.
-  Efectos al cambiar de estado. Llegar a `CANCELLED` desde `PUBLISHED`, `SOLD_OUT` o `SUSPENDED` tiene que disparar el aviso al ticket-service para los reintegros. Además, el ticket-service tiene que vender solo cuando el evento está en `PUBLISHED`; `SUSPENDED` también debe bloquear la venta. Durante la suspensión se aceptan devoluciones, con la regla de 72 horas (ver US-TKT-14 en `user-stories.md`)
- Estados nuevos. Si alguien agrega un estado al enum y no lo carga en el mapa, transition falla con un NullPointerException en vez de un mensaje claro. Un test que recorra todos los valores lo detecta.