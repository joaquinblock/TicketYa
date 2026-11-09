---
name: implementar-funcionalidad
description: Aplicar siempre que se implemente, modifique o planifique una funcionalidad de TicketYa (endpoint, regla de negocio, job o integración entre servicios). Obliga a contrastar con el enunciado del README y a trabajar de a una funcionalidad completa, con su controller, service, repository, errores, tests y request de Postman.
---

# Implementar una funcionalidad

El enunciado del trabajo está completo en `README.md`. Es la fuente de verdad sobre qué hay que construir.

## 1. Antes de escribir código

1. Leer en `README.md` lo que aplica a la funcionalidad:
   - El punto de **Funcionalidad** (numerado del 1 al 12).
   - Las **consideraciones no funcionales** que la afectan.
   - La tabla de **Modalidad de entrega**, para saber qué corresponde a esta entrega y qué no se adelanta.
   - Las **Tareas** y el **Entregable** de esa entrega, en especial lo que piden de testing.
2. Leer la historia y sus criterios en `docs/user-stories.md`.
3. Leer el contrato y las decisiones en `docs/architecture/<servicio>.md`, y los códigos en `docs/architecture/error-handling.md`.
4. Si el enunciado, la historia y el diseño no coinciden, frenar y avisar antes de implementar. No elegir en silencio.
5. Explicar y acordar antes cualquier clase, módulo o configuración que no sea obvia, con la alternativa más simple.

## 2. Una funcionalidad por vez

Una funcionalidad es un comportamiento que se puede probar de punta a punta, por ejemplo "cobrar una orden". Corresponde a una issue, una rama (`<tipo>/<issue>-<slug>`) y un PR que la cierra. Si es demasiado grande para revisarla de una vez, se divide la issue. Se termina entera antes de empezar la siguiente, y lleva solo lo que necesita:

| Parte | Qué lleva |
|---|---|
| Controller | El método o los métodos del endpoint, con sus anotaciones de OpenAPI y códigos de error |
| Service | El método con la lógica de negocio |
| Repository | Las consultas que hagan falta |
| DTO y mapper | Los records de request y response, y su conversión |
| Errores | El código nuevo en `<Servicio>ErrorCode`, también listado en `error-handling.md` |
| Tests | Unitario del service y `@WebMvcTest` del controller. Integración real si depende de concurrencia o de una restricción de la base |
| Postman | Sus requests, con ejemplos y assertions, en una carpeta de `docs/postman/<servicio>.postman_collection.json` |
| Datos de prueba | El seeder, si la funcionalidad los necesita |

No dejar código preparado para la funcionalidad siguiente.

## 3. Al terminar

1. Correr los tests del módulo: `./mvnw -pl <servicio> -am test`.
2. Verificar uno por uno los criterios de aceptación de la historia, e informar los que no se cubrieron.
3. Actualizar `docs/architecture/<servicio>.md` si cambió el contrato o una decisión.
4. Mostrar el resultado y esperar el visto bueno antes de pasar a la siguiente funcionalidad.
