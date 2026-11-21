# Formato y reglas comunes a todos los lentes

## Evidencia

- Cada hallazgo tiene un escenario concreto: con estos datos o en este orden de eventos, pasa esto, y se esperaba esto otro. "Podría haber un problema de concurrencia" no es un hallazgo.
- Si el hallazgo depende de cómo se comporta Spring, Hibernate, Jackson, Spring AMQP u otra librería, confirmalo en la versión del proyecto: el `pom.xml`, el código fuente del jar o los tests. No te fíes de lo que dice el tracker ni de la memoria. En #67 el tracker afirmaba algo sobre `AFTER_COMMIT` que era falso en Spring 7.
- Mirá el código completo alrededor del cambio, no solo las líneas del diff.
- Lo que el tracker marca como decisión confirmada por el usuario no es un hallazgo, salvo que no cumpla un criterio o el enunciado.
- Lo que está en "Fuera de alcance" no es un hallazgo.

## Salida

Devolvé solo esto, sin introducción:

```
### H<n>: <una línea>
- Gravedad: bloquea | importante | menor
- Dónde: <archivo:línea>
- Escenario: <datos o secuencia → qué pasa → qué se esperaba>
- Evidencia: <qué leíste o corriste que lo muestra>
- Corrección: <la más simple que lo resuelve>
```

Si no encontrás nada, devolvé `Sin hallazgos.` y una línea con lo que miraste. Mejor pocos hallazgos ciertos que muchos dudosos.
