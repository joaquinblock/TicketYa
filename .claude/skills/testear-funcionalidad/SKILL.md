---
name: testear-funcionalidad
description: Aplicar siempre que se escriban, revisen o amplíen tests de TicketYa (unitarios, @WebMvcTest, persistencia, concurrencia, integración entre servicios). Define el análisis de casos borde, cómo simular la llegada de datos en cada nivel y qué garantías exigen base o concurrencia reales.
---

# Testear una funcionalidad

Lo que la entrega exige está en `README.md`, en **Testing**. La regla central: cada test usa el nivel más barato capaz de detectar el error que busca, y una garantía de la base o de la concurrencia nunca se prueba con mocks.

## 1. Análisis de casos borde

Antes de escribir tests, recorrer el componente como Ingeniero de QA, con enfoque de caja blanca y caja negra, y presentar:

1. **Entradas críticas**: valores límite, nulos, vacíos y tipos inesperados.
2. **Casos borde de negocio**: reglas que se cruzan, se anulan o compiten.
3. **Error y resiliencia**: fallas de red, timeouts y excepciones del sistema.
4. **Tabla de casos**: `ID | Escenario | Entrada | Resultado esperado | Tipo (Unit/Integration)`.
5. **Tests reales** de los casos más críticos, como mínimo dos, escritos en el proyecto.

Revisar siempre en un endpoint:

| Zona | Casos |
|---|---|
| Número | 0, negativo, mínimo y máximo aceptados, un dígito o decimal de más, overflow de `Long` |
| Coerción de JSON | `1.5` en un entero, número o `"0"` en un enum, enum en minúsculas, texto en un número |
| Texto | `null`, `""`, solo espacios, largo exacto de la columna y uno más |
| Cuerpo | `{}`, `[]`, JSON cortado, no JSON, vacío, `Content-Type` incorrecto |
| Estado | Repetir la operación, operar tras un rechazo, recurso en cada estado posible |
| Idempotencia | Misma clave con el mismo cuerpo, con otro cuerpo y después de un fallo |
| Dependencia | La llamada externa falla o tarda: qué queda guardado y qué ve quien llamó |
| Orden | El efecto ocurre antes o después de lo que debe (`InOrder`) y no se llama a lo que no corresponde |

El límite del DTO y el de la columna tienen que coincidir. Se prueba el borde válido y el primer valor inválido.

## 2. Cómo simular la llegada de datos

| Qué se prueba | Herramienta | Cómo entra el dato | Qué es real |
|---|---|---|---|
| Regla de negocio | `@ExtendWith(MockitoExtension.class)` | Record del request, entidades con su builder | Service, mapper y entidad |
| Contrato HTTP y validación | `@WebMvcTest` + `@MockitoBean` del service | JSON como texto (text block o `@ValueSource`), nunca serializando el DTO | Jackson, Bean Validation y `GlobalExceptionHandler` |
| Unicidad, columnas y consultas | `@SpringBootTest` + perfil `test` | Repository con `saveAndFlush`, se relee con `findById` | MySQL |
| Flujo de punta a punta | `RANDOM_PORT` + `RestClient` | HTTP real; el estado previo se crea con la API o el repository | Toda la aplicación y MySQL |
| Concurrencia | `RANDOM_PORT` o service real + `CountDownLatch` | Hilos soltados a la vez | Todo |
| Llamada a otro servicio | `MockRestServiceServer` | Respuestas 2xx, 4xx, 5xx y timeout | Cliente, conversión y manejo de errores |

El JSON va como texto porque es lo que manda el cliente. Serializar el DTO no permite probar bodies malformados y esconde los cambios de contrato.

Recursos de Mockito:

- `thenAnswer(invocation -> invocation.getArgument(0))` para que `save` devuelva lo que recibe.
- `thenReturn(true, false)` para colisiones y reintentos.
- `ArgumentCaptor` o capturar lo guardado cuando el resultado está en lo que se persiste.
- `verifyNoInteractions` para probar que un rechazo ocurrió antes de cobrar.

Se mockea en el borde del sistema, como `PaymentGateway`, y el resto se construye real. No mockear mappers estáticos, records ni entidades.

## 3. Concurrencia

Tirar requests en paralelo con `curl` no prueba nada, porque no garantiza que coincidan. Los hilos se alinean con dos latches y se afirma el estado final de la base:

```java
CountDownLatch ready = new CountDownLatch(threads);
CountDownLatch start = new CountDownLatch(1);

List<Future<Integer>> attempts = IntStream.range(0, threads)
        .mapToObj(attempt -> executor.submit(() -> {
            ready.countDown();
            start.await();
            return buySeatAndReturnHttpStatus(attempt);
        }))
        .toList();

assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
start.countDown();

assertThat(statusesOf(attempts)).containsOnlyOnce(201);
assertThat(soldTicketsOfTheSeat()).isEqualTo(1);
```

Cada resultado se consume con `future.get(timeout)`, y las excepciones inesperadas hacen fallar el test. Para el bloqueo pesimista, un hilo toma el lock dentro de `TransactionTemplate`, avisa con un latch y lo mantiene hasta que el segundo intento termine.

## 4. Aislamiento

- Cada test crea sus propios datos, con ids y claves únicos, y solo afirma sobre ellos.
- En una base compartida no usar `deleteAll` ni contar la tabla entera.
- Los tests no dependen del orden ni del estado que deja otro test.
- El tiempo se controla con un `Clock` inyectado cuando la regla depende de la fecha, como una reserva que vence. Nada de `Thread.sleep`.

## 5. Qué no hacer

- **H2 en modo MySQL** para garantías de la base: no se comporta igual en locks ni restricciones. Usar el MySQL del `compose.yaml` o Testcontainers.
- **`@SpringBootTest` para un controller** con el service mockeado: `@WebMvcTest` lo prueba mucho más rápido.
- **`@Order` con estado compartido y `assumeTrue`**: una falla arrastra a las siguientes y se saltean sin avisar.
- **Tragarse timeouts** en helpers de espera: el test puede pasar sin haber corrido la carrera.
- **Llamar `IntegrationTest` a una clase con todo mockeado**: el nombre promete una garantía que no da.
- **Verificar llamadas internas** en vez del resultado, salvo que la llamada sea el efecto, como cobrar o no cobrar.

## 6. Convenciones

- Nombre: `method_scenario_expectedResult`, en inglés y sin comentarios.
- Cuando el mismo comportamiento varía solo por el dato, `@ParameterizedTest`.
- Afirmar `status`, `error` y, si importa, el campo en `detail`, no solo el status.
- Un test que necesita base real debe notarse en su nombre o en su clase.

## 7. Al terminar

1. Correr `./mvnw -pl <servicio> -am test`, con Docker prendido.
2. Correr la colección de Postman contra el servicio levantado.
3. Informar lo que no se pudo probar y por qué. Si un test destapa un bug, se corrige el código, no se ajusta el test.
