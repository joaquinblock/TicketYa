package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.group10.event.entity.EventType;

import java.time.LocalDateTime;

/**
 * Alta de un evento. La organización y el organizador no van acá: llegan por header y se validan contra el
 * auth-service. El estado tampoco: todo evento nace en {@code DRAFT}.
 */
@Schema(description = "Alta de un evento; se crea en borrador")
public record EventCreateRequest(
        @Schema(example = "Los Serranos en vivo")
        @NotBlank @Size(max = 255)
        String name,

        @Schema(description = "Opcional", example = "Presentación del nuevo disco")
        @Size(max = 2000)
        String description,

        @Schema(example = "CONCERT")
        @NotNull
        EventType type,

        @Schema(description = "Lugar donde se hace", example = "1")
        @NotNull @Positive
        Long venueId,

        @Schema(description = "Fecha y hora del evento; tiene que ser futura", example = "2026-12-12T21:00:00")
        @NotNull @Future
        LocalDateTime dateTime,

        @Schema(description = "Si es true, la venta pasa por la fila virtual y hace falta saleStart", example = "true")
        @NotNull
        Boolean highDemand,

        @Schema(description = "Apertura de la venta; opcional salvo en alta demanda. Anterior a dateTime",
                example = "2026-11-20T10:00:00")
        LocalDateTime saleStart,

        @Schema(description = "Tope de entradas por cliente; opcional (sin tope)", example = "4")
        @Positive
        Integer maxTicketsPerCustomer
) {}
