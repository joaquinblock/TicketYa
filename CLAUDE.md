@AGENTS.md

## Específico de Claude Code
- Los reviewers están en `~/.claude/agents/`. No los lances directamente: `/rdd` decide cuáles hacen falta.
- Si el contexto se llena a mitad de una tarea, actualizá el Log del tracker y sugerí `/clear`: el tracker conserva el estado.
- El hook `agent-guard.mjs` y `.githooks/commit-msg` bloquean firmas de IA, commits en `main`, mensajes mal formados y cambios a tests commiteados mientras exista `.work/tests.lock`. Si un hook te bloquea, leé el mensaje y corregí; no intentes esquivarlo.
