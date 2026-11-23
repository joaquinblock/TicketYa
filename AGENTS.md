# AGENTS.md

> Mapa del proyecto para cualquier agente. Corto a propósito: se carga en cada sesión.

## Comandos
- Tests de un servicio: `./mvnw -pl <servicio> -am test` (con Docker prendido: los tests de persistencia usan MySQL)
- Un solo test: `./mvnw -pl <servicio> -am test -Dtest=<Clase> -Dsurefire.failIfNoSpecifiedTests=false`
- Verificación completa, para saber que no se rompió nada: `./mvnw verify`
- Formato y lint: no configurado
- Compila con Java 25. Si Maven dice `release version 25 not supported`, apuntá `JAVA_HOME` a un JDK 25.

## Flujo
`/ticket` → PR de stories → `/tracker` → `/clear` → `/implement` → `/clear` → `/rdd` → `/retro` → PR. El detalle está en `docs/flujo-de-trabajo.md`.

- Sin tracker en `.work/NUM-*.md` no se escribe código de producción.
- Un test commiteado no se cambia para que pase. Si parece mal, se anota en el Log del tracker y se pregunta.
- No se toca nada de lo que el tracker marca como "Fuera de alcance".
- Un patrón nuevo (abstracción, librería, estructura de carpetas, manejo de errores) necesita un ADR en `docs/adr/`.

## Proyecto
- El enunciado del trabajo está en `README.md` y es la fuente de verdad sobre qué construir.
- Para implementar y testear, cargá las skills del proyecto `implementar-funcionalidad` y `testear-funcionalidad`.

## Dónde buscar
- Cómo funciona el flujo → `docs/flujo-de-trabajo.md`
- Arquitectura → `docs/architecture.md`
- Roles y glosario del dominio → `docs/domain/`
- Decisiones tomadas → `docs/adr/`
- Atributos de calidad → `docs/quality-attributes.md`
- Tickets → `docs/stories/`
