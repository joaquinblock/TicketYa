---
name: retro
description: Retro de un ticket de TicketYa después de /rdd. Junta las correcciones y los aprendizajes de la rama (Log del tracker, hallazgos de la review, cosas que se corrigieron en la sesión) y los lleva a su lugar (docs/learnings/, docs de arquitectura, ADR, skills del proyecto, la story y su DoD), propone los cambios a instrucciones para aprobar y deja la rama lista para push y PR. Usar siempre que el usuario pida /retro, "hacé la retro", "cerrá el ticket", "registrá lo que aprendimos" o quiera cerrar un tracker de .work/ antes de abrir el PR.
---

# /retro: correcciones y aprendizajes a su lugar

Va después de `/rdd` y antes del PR. Lo que se aprendió en un ticket solo sirve si queda donde el próximo lo va a encontrar. El tracker es temporal (`.work/` está en el gitignore), así que lo que no salga de ahí se pierde.

```
juntar → filtrar → ubicar → ✋ aprobás → aplicar → cerrar story y tracker → ✋ push y PR
```

## 1. Juntar

- El tracker `.work/NUM-*.md` (lo saca del número de la rama): Investigación, Decisiones y sobre todo el Log, que dice qué salió distinto de lo planeado y qué quedó "para #otro".
- La línea de `/rdd` en el Log: lo que se dejó documentado o pendiente.
- La conversación actual, si la hay: correcciones del usuario, cosas que se probaron y no funcionaron.
- `git log --oneline <base>..HEAD` y el diff, para ver lo que realmente quedó.

## 2. Filtrar

Hay cuatro clases de candidatos:

1. **Correcciones:** algo que quedó falso en el código, las docs o el tracker, como un comentario que explica mal, una doc que contradice al código o una afirmación de la Investigación que resultó errónea. Se corrigen.
2. **Aprendizajes técnicos:** un comportamiento no obvio de una librería o del sistema que costó descubrir y que le va a volver a pasar a alguien.
3. **Aprendizajes de proceso:** algo que una instrucción (skill, `AGENTS.md`, flujo) tendría que haber evitado o que conviene repetir.
4. **Pendientes para otro ticket:** algo que el siguiente tiene que saber, por ejemplo "para #152: el consumidor tiene que convertir según el tipo del parámetro".

Queda afuera lo que ya dice el código, lo que se ve en el historial de git, lo que solo importó en esta sesión y lo obvio para alguien que conoce Spring. Mejor dos aprendizajes que cambian cómo se trabaja que diez que nadie va a leer.

**Antes de escribir un aprendizaje técnico, confirmalo** en el código o en el jar de la versión del proyecto. No lo copies del tracker: en #67 el tracker decía que una excepción en `AFTER_COMMIT` le llegaba al que llamó, y el bytecode de spring-tx mostró lo contrario.

## 3. Ubicar

| Qué | Dónde |
|---|---|
| Comportamiento no obvio de una librería o herramienta | `docs/learnings/<tema>.md`. Si el tema ya tiene archivo, se agrega ahí. |
| Cómo se comporta un servicio (qué publica, qué pasa si algo falla) | `docs/architecture/<servicio>.md` |
| Cambió el estado de un atributo de calidad | `docs/quality-attributes/<atributo>.md`, en "Estado" |
| Patrón o decisión de arquitectura | ADR nuevo en `docs/adr/` (desde `0000-plantilla.md`) o una línea en las consecuencias de uno existente |
| Cómo implementar o testear en este repo | `.claude/skills/implementar-funcionalidad/` o `testear-funcionalidad/` |
| Cómo funciona el flujo | `docs/flujo-de-trabajo.md`, o las skills `rdd` y `retro` |
| Regla que aplica a todo y siempre | `AGENTS.md`, solo si es corta y de verdad general: se carga en cada sesión |
| Pendiente para otro ticket | La story de ese ticket en `docs/stories/`, en "Notas técnicas" |

Formato de `docs/learnings/`: un título con el tema, una línea que dice de qué ticket sale y en qué versión se vio, y bullets con **la afirmación en negrita**, la explicación y un `Ejemplo:` con la ruta del código. Mirá los archivos que ya están.

## 4. Propuesta y aprobación ✋

Mostrar la lista agrupada por destino, con el texto exacto o un resumen de una línea para cada cambio. Separar aparte los **cambios a instrucciones** (`AGENTS.md`, `CLAUDE.md`, skills, `docs/flujo-de-trabajo.md`): cambian cómo trabajan todos los agentes y necesitan un sí explícito, uno por uno si son varios.

## 5. Aplicar

- Correcciones de código (aunque sea un comentario): commit `fix:` aparte.
- Aprendizajes y docs: commit `docs:`. Un cambio a instrucciones va en su propio commit, para poder revertirlo solo.
- Commits con el formato de `.githooks/commit-msg`: `tipo: descripción` de 72 caracteres como máximo, en castellano y sin firmas de IA.
- No se cambian tests commiteados ni se toca lo que está fuera de alcance.

## 6. Cerrar story y tracker

- En la story: actualizar **Estado** y tildar la DoD que corresponda (cada criterio con su test, review aprobada, documentación). Si el PR cierra solo una parte, el Estado lo dice. Incluirlo en el commit `docs:`.
- En el tracker: Estado en el encabezado y una línea en el Log con lo que se registró, dónde y los hashes.
- Si `.work/tests.lock` existe, recordarle al usuario que solo una persona lo borra, y que lo haga si ya no hay tests que proteger. La retro no lo borra.

## 7. Push y PR ✋

Pedir permiso antes del push y antes de abrir el PR. Base: `main`, o la rama sobre la que va apilado según el tracker.

Descripción del PR, corta: primero el punto central (qué cambia para quien usa el sistema), después cómo funciona en dos o tres líneas, después las clases creadas o modificadas con una línea sobre qué hace cada una, y al final `Closes #NUM` o `Part of #NUM` si cubre solo una parte. Agregar la URL del PR al tracker.
