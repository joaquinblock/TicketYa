package org.group10.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.group10.payment.entity.RefundReason;

import java.math.BigDecimal;

@Schema(description = "Reintegro de una entrada")
public record RefundRequest(
        @Schema(description = "Entrada de ticket-service", example = "7")
        @NotNull @Positive
        Long ticketId,

        @Schema(description = "Orden de ticket-service", example = "10")
        @NotNull @Positive
        Long orderId,

        @Schema(description = "Monto a reintegrar", example = "25000.00")
        @NotNull @Positive @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @Schema(description = "Motivo del reintegro", example = "RETURN_REQUEST")
        @NotNull
        RefundReason reason,

        @Schema(description = "Devolución de ticket-service, solo con el motivo RETURN_REQUEST", example = "3")
        @Positive
        Long returnRequestId
) {

    @Schema(hidden = true)
    @AssertTrue(message = "returnRequestId es obligatorio con el motivo RETURN_REQUEST y no se admite con otro motivo")
    public boolean isReturnRequestIdPresentOnlyForAReturnRequest() {
        return reason == null || (reason == RefundReason.RETURN_REQUEST) == (returnRequestId != null);
    }
}
