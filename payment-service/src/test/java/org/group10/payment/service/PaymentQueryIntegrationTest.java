package org.group10.payment.service;

import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.OrderPaymentResponse;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentQueryIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void findPaymentsOfOrder_paymentsInEveryStatus_returnsThemOldestFirstWithTheirStoredData() {
        Long orderId = uniqueOrderId();
        paymentRepository.saveAndFlush(payment(orderId, PaymentMethod.DEBIT_CARD, PaymentStatus.REJECTED, "Fondos insuficientes"));
        paymentRepository.saveAndFlush(payment(orderId, PaymentMethod.ACCOUNT_MONEY, PaymentStatus.REJECTED, "Tarjeta vencida"));
        paymentRepository.saveAndFlush(payment(orderId, PaymentMethod.CREDIT_CARD, PaymentStatus.APPROVED, null));

        List<OrderPaymentResponse> payments = paymentService.findPaymentsOfOrder(orderId);

        assertThat(payments).extracting(OrderPaymentResponse::status)
                .containsExactly(PaymentStatus.REJECTED, PaymentStatus.REJECTED, PaymentStatus.APPROVED);
        assertThat(payments).extracting(OrderPaymentResponse::failureReason)
                .containsExactly("Fondos insuficientes", "Tarjeta vencida", null);
        assertThat(payments).extracting(OrderPaymentResponse::paymentMethod)
                .containsExactly(PaymentMethod.DEBIT_CARD, PaymentMethod.ACCOUNT_MONEY, PaymentMethod.CREDIT_CARD);
        assertThat(payments).allSatisfy(payment -> {
            assertThat(payment.createdAt()).isNotNull();
            assertThat(payment.amount()).isEqualByComparingTo("25000.00");
        });
    }

    @Test
    void findPaymentsOfOrder_paymentsOfAnotherOrder_areNotIncluded() {
        Long orderId = uniqueOrderId();
        paymentRepository.saveAndFlush(payment(orderId, PaymentMethod.CREDIT_CARD, PaymentStatus.PENDING, null));
        paymentRepository.saveAndFlush(payment(uniqueOrderId(), PaymentMethod.CREDIT_CARD, PaymentStatus.APPROVED, null));

        assertThat(paymentService.findPaymentsOfOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.orderId()).isEqualTo(orderId));
    }

    @Test
    void findPaymentsOfOrder_orderWithoutPayments_failsAsNotFound() {
        assertThatThrownBy(() -> paymentService.findPaymentsOfOrder(uniqueOrderId()))
                .isInstanceOfSatisfying(TicketYaException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    private Payment payment(Long orderId, PaymentMethod method, PaymentStatus status, String failureReason) {
        return Payment.builder()
                .orderId(orderId)
                .idempotencyKey(UUID.randomUUID().toString())
                .paymentMethod(method)
                .amount(new BigDecimal("25000.00"))
                .status(status)
                .failureReason(failureReason)
                .build();
    }

    private Long uniqueOrderId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }
}
