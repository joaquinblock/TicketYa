# Lente: corrección y alcance

Es el lente principal: responde si la rama hace lo que el trabajo pide. Leé también `_formato.md`, que está en esta misma carpeta.

## Qué leer

- El tracker: "Qué se construye", "Decisiones", "Plan", "Fuera de alcance" y el Log.
- La story: criterios de aceptación y fuera de alcance.
- `README.md`: el punto de Funcionalidad, las consideraciones no funcionales y la tabla de Modalidad de entrega.
- `docs/architecture/<servicio>.md` y `docs/architecture/error-handling.md` para los contratos y los códigos de error.
- El diff contra la base y el código que lo rodea.

## Qué buscar

1. **Cada criterio de aceptación se cumple en el código.** Recorré los CA uno por uno y nombrá dónde se cumple cada uno. Si un CA no tiene código que lo cumpla, o lo cumple a medias, es `bloquea`.
2. **Lo que pide el enunciado y no está en la story.** Si el README exige algo para esta funcionalidad en esta entrega y nadie lo cubre, es un hallazgo aunque la story no lo diga.
3. **Casos borde del comportamiento:** valores nulos o vacíos, estados intermedios (orden vencida, evento cancelado, entrada ya usada), el segundo intento, el recurso que no existe. Contá solo los que llevan a una respuesta incorrecta, no los teóricos.
4. **Contrato:** los códigos HTTP y los cuerpos de error coinciden con `error-handling.md` y con lo que documenta el servicio. Los nombres de campos coinciden con lo que acordó la story.
5. **Sigue el tracker:** las decisiones confirmadas se respetaron. Si el código se apartó, ¿quedó anotado en el Log?
6. **Alcance:** nada del diff toca lo que el tracker o la story marcan como fuera de alcance, y no hay trabajo de más (abstracciones, configuraciones o endpoints que nadie pidió).
7. **Patrón nuevo sin ADR:** una abstracción, librería, estructura de carpetas o manejo de errores nuevo necesita un ADR en `docs/adr/`, o una línea en uno existente si ya lo cubre.

## Qué no es de este lente

Si los tests prueban bien cada criterio y si las docs están al día es de `tests-y-docs.md`. Las carreras y las transacciones son de `concurrencia-y-persistencia.md`. Si encontrás algo de eso que sea grave, anotalo igual.
