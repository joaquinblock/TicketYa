package org.group10.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.payment.dto.RefundOutcome;
import org.group10.payment.dto.RefundRequest;
import org.group10.payment.dto.RefundResponse;
import org.group10.payment.service.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/refunds")
@RequiredArgsConstructor
@Tag(name = "Reintegros", description = "Devolución del dinero de una entrada, solo entre servicios")
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    @Operation(summary = "Reintegrar una entrada", description = "Lo llama ticket-service. El gateway no reenvía esta ruta.")
    @ApiResponse(responseCode = "201", description = "Reintegro acreditado")
    @ApiResponse(responseCode = "200", description = "La entrada ya tenía ese reintegro y no se acredita de nuevo")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "payment_not_found: la orden no tiene pagos",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "payment_not_refundable: sin pago aprobado o supera lo cobrado. data_conflict: la entrada ya tiene un reintegro con otros datos",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "502", description = "refund_failed: la pasarela no acreditó y se puede reintentar",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<RefundResponse> refund(@Valid @RequestBody RefundRequest request) {
        RefundOutcome outcome = refundService.refund(request);

        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.refund());
    }
}
