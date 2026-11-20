# Flujo de trabajo con agentes

Cómo se trabaja en este repo, para personas y para agentes.

## El flujo

```
 IDEA
  │
  ▼
/ticket ───── clasifica (story / enabler / spike / bug), entrevista,
  │           criterios verificables          → docs/stories/NUM-*.md
  │           rama docs/stories-<funcionalidad> y PR
  │  ✋ el equipo revisa y mergea a main (eso es la aprobación)
  ▼
/tracker ──── en main, sin rama: investiga con un subagente, entrevista
  │           sobre diseño y casos a probar     → .work/NUM-*.md
  │  ✋ aprobás el plan y "Qué se prueba" (en lenguaje natural)
  │  🧹 /clear
  ▼
/implement ── crea la rama feat/NUM-* desde main
  │           por cada ítem: test en rojo → commit test: → verde → commit
  │           (los tests commiteados quedan solo-agregar: .work/tests.lock)
  │  🧹 /clear
  ▼
/rdd ──────── triage → solo los lentes necesarios → verificador
  │  ✋ aprobás las correcciones
  ▼
/retro ────── correcciones y aprendizajes → su lugar
  │  ✋ aprobás los cambios a instrucciones y el push
  ▼
 PR ───────── ✋ review humana y merge
```

## Dónde vive cada cosa

| Qué | Dónde | Vida |
|---|---|---|
| Mapa del proyecto para agentes | `AGENTS.md` (+ `CLAUDE.md`, que lo importa) | Permanente, se carga siempre |
| Skills del flujo y reviewers | `~/.claude/skills/`, `~/.claude/agents/` | De cada dev, se cargan al usarse |
| Reglas por zona del código | `.claude/rules/` | Permanente, se carga al tocar esos paths |
| Conocimiento del sistema | `docs/` | Permanente, se lee cuando hace falta |
| Tickets | `docs/stories/` | Permanente |
| Trabajo en curso | `.work/NUM-*.md` | Temporal (gitignored) |
| Aprendizajes | `docs/learnings/` | Permanente, no se carga solo |

## Qué está forzado por código

- **Sin firmas de IA:** `attribution` vacío, el hook `agent-guard.mjs` y `.githooks/commit-msg`.
- **Sin commits en `main`** desde un agente: `agent-guard.mjs`.
- **Formato de commits y ramas:** `.githooks/commit-msg`.
- **Tests solo-agregar** mientras existe `.work/tests.lock`: `agent-guard.mjs`. Solo una persona borra el candado.
- **Push, PR y merge con permiso:** reglas `ask` de `~/.claude/settings.json`.

## Tipos de ticket

- **Story:** valor para alguien que usa el producto. Nunca "Como sistema", "Como desarrollador" ni "Como <servicio>".
- **Enabler:** trabajo técnico (refactor, infra, deuda, tooling), con criterios de terminado verificables.
- **Spike:** investigación con tiempo acotado. El entregable es un ADR.
- **Bug:** siempre arranca con un test que lo reproduce.

La documentación no es un ticket: es parte de la Definition of Done de cada uno.
