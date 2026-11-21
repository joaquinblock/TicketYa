# Lente: tests y docs

Responde si lo que se construyó está probado de verdad y documentado donde el equipo lo va a buscar. Leé también `_formato.md`, que está en esta misma carpeta, y la skill del proyecto `.claude/skills/testear-funcionalidad/SKILL.md`, que define cómo se testea acá.

## Tests

1. **Cada criterio tiene un test que fallaría si el criterio no se cumpliera.** Compará la sección "Qué se prueba" del tracker con los tests reales. Un test que pasa aunque se borre la línea que importa no prueba nada: pensá qué mutación del código lo dejaría verde.
2. **Nivel correcto:** una garantía de la base (`UNIQUE`, `UPDATE` condicional, `@Version`, rollback) o de la concurrencia se prueba con MySQL real y con hilos reales, no con mocks. Un mock de repositorio en esos casos es `importante`.
3. **Aserciones que dicen algo:** sobre el estado final (la fila en la base, el mensaje publicado, el código y el cuerpo HTTP), no solo `isOk()` o "no tiró excepción". Para "no pasa X", que el test verifique que X no pasó.
4. **Tests estables:** nada de `Thread.sleep` para esperar a otro hilo, relojes reales donde importa la hora (se usa `Clock`) ni orden entre tests.
5. **Tests commiteados:** con `.work/tests.lock`, ningún test que ya estaba commiteado antes de la rama cambió para pasar. Revisalo con `git diff <base>...HEAD -- '**/src/test/**'`: un archivo de test existente con líneas borradas o cambiadas es `bloquea`.

## Docs

Por la Definition of Done, la documentación afectada es parte del ticket:

- `docs/architecture/<servicio>.md`: endpoints, mensajes y comportamiento nuevos o cambiados.
- `docs/quality-attributes/*.md`: si cambió el estado de un atributo (de pendiente a implementado, por ejemplo).
- `docs/postman/`: un request por endpoint nuevo o cambiado.
- OpenAPI en el controller: códigos de error documentados.
- ADR: si hay un patrón nuevo, que el ADR exista o que el existente diga que lo cubre.
- Comentarios en el código que digan algo falso. En #67 el comentario del publicador explicaba mal por qué se atrapaba la excepción.

Una doc que contradice al código es `importante`. Una que falta es `menor`, salvo que la DoD de la story la nombre.
