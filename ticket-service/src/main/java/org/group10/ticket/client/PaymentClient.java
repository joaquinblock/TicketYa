package org.group10.ticket.client;

import org.group10.ticket.client.dto.ChargeRequest;
import org.group10.ticket.client.dto.RefundRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface PaymentClient {

    @PostExchange("/internal/payments")
    void charge(@RequestBody ChargeRequest request);

    @PostExchange("/internal/refunds")
    void refund(@RequestBody RefundRequest request);
}
