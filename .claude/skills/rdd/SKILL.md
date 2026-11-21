---
name: rdd
description: Review de la rama de un ticket de TicketYa después de /implement. Hace un triage del diff, corre en paralelo solo los lentes que hacen falta (corrección y alcance, tests y docs, concurrencia y persistencia, comunicación y seguridad), filtra los hallazgos con un verificador y propone correcciones para aprobar. Usar siempre que el usuario pida /rdd, "revisá la rama", "hacé la review" o quiera revisar lo implementado de un tracker de .work/ antes de la retro y el PR.
---

# /rdd: review de la rama

Va después de `/implement` y antes de `/retro`. El objetivo principal del trabajo es **cumplir lo que pide el enunciado** (`README.md`) y los criterios de la story. La review existe para confirmar eso y para atrapar los riesgos reales que lo pondrían en duda. No es una auditoría de estilo.

```
contexto → triage → lentes en paralelo → verificador → ✋ aprobás → correcciones → Log
```

## 1. Contexto

1. Sacar el número del ticket de la rama (`feat/NUM-*`, `fix/NUM-*`) y leer `.work/NUM-*.md`. Sin tracker, frenar y avisar: no hay contra qué revisar.
2. Leer la story que nombra el tracker (`docs/stories/NUM-*.md`) y, en `README.md`, el punto de Funcionalidad y las consideraciones no funcionales que le tocan.
3. Definir la base del diff. Por defecto es `main`, pero si el tracker dice que la rama va apilada sobre otra, la base es esa. Usar `git diff <base>...HEAD` y `git log --oneline <base>..HEAD`.
4. Mirar el Log del tracker: si la suite no figura en verde después del último commit de código, correrla (`./mvnw -pl <servicio> -am test`, con Docker prendido). Una suite roja va primera en el reporte.

## 2. Triage

Elegir los lentes según lo que el diff toca. Los archivos de cada lente están en `lentes/`.

| Lente | Cuándo corre |
|---|---|
| `correccion-y-alcance.md` | Siempre. Es el lente principal. |
| `tests-y-docs.md` | Siempre que haya código de producción en el diff. |
| `concurrencia-y-persistencia.md` | Solo con un riesgo concreto: transacciones (`@Transactional`, `TransactionOperations`), `UPDATE` condicionales, `@Version`, locks, entidades o esquema nuevos, cupos o asientos, algo que dos requests a la vez puedan pisar, idempotencia. |
| `comunicacion-y-seguridad.md` | Solo con un riesgo concreto: `client/`, `messaging/`, `RestClient`, `RabbitTemplate`, `@RabbitListener`, timeouts, rutas `/internal/**`, gateway, JWT, `@PreAuthorize`, config de seguridad, datos personales en logs. |

Estos dos últimos son importantes, pero corren por riesgo y no por costumbre. Que el diff tenga un `@Transactional` que ya existía no alcanza: tiene que haber algo nuevo que pueda fallar. Ante la duda, correrlo.

Antes de lanzar nada, decirle al usuario en dos o tres líneas qué lentes corren y por qué, y cuáles quedan afuera y por qué.

## 3. Lentes en paralelo

Lanzar un subagente `general-purpose` por lente, **todos en el mismo mensaje**. El prompt de cada uno es:

```
Sos un reviewer de TicketYa. Leé y seguí <ruta absoluta a lentes/X.md>.
- Tracker: <ruta>   - Story: <ruta>   - Base del diff: <base>
- Servicios tocados: <lista>
No edites ni commitees nada. Devolvé los hallazgos en el formato del lente.
```

Los lentes ya traen el formato de salida y la regla de evidencia. No pegarles el diff: que lo lean ellos, así cada uno mira el código completo alrededor del cambio.

## 4. Verificador

Con todos los hallazgos juntos, lanzar un subagente `general-purpose` con `verificador.md`, el tracker, la base y la lista de hallazgos tal como volvieron. Devuelve cada uno como CONFIRMADO, PLAUSIBLE o DESCARTADO, sin repetidos.

Si no hubo hallazgos, saltear este paso.

## 5. Reporte y aprobación ✋

Mostrar solo lo confirmado y lo plausible, de lo más grave a lo menos grave:

```
| # | Gravedad | Dónde | Qué pasa | Corrección propuesta |
```

- **Gravedad:** `bloquea` (no cumple un criterio o el enunciado, se rompe algo, se pierden datos), `importante` (un riesgo real que hay que resolver o documentar), `menor` (vale la pena pero no frena el PR).
- Los descartados van en una línea al final con el motivo, para que se vea que se miraron.
- Si no queda nada, decirlo claro: "la review no encontró problemas", con los lentes que corrieron.

Preguntar qué correcciones se aplican. Un hallazgo puede resolverse con código, con documentación ("queda así y se explica por qué") o no tocarse. Eso lo decide el usuario.

## 6. Aplicar lo aprobado

- **Comportamiento mal:** primero un test nuevo que lo muestre en rojo → commit `test:` → corrección → commit `fix:`. Mientras exista `.work/tests.lock`, los tests commiteados no se cambian: se agregan tests o clases nuevas.
- **Un test commiteado que parece mal:** no se toca. Se anota en el Log y se le pregunta al usuario.
- **Solo docs:** commit `docs:`.
- Nada de lo que el tracker marca como "Fuera de alcance". Si una corrección lo necesita, se anota como pendiente.
- Commits con el formato de `.githooks/commit-msg`: `tipo: descripción` de 72 caracteres como máximo, en castellano y sin firmas de IA.
- Después de corregir, volver a correr la suite del servicio.

## 7. Log del tracker

Agregar una línea con la fecha: qué lentes corrieron, cuántos hallazgos quedaron, qué se corrigió (con el hash) y qué se dejó documentado o pendiente. Terminar con "Sigue `/retro`".

La review no marca la DoD de la story: eso lo cierra `/retro`.
