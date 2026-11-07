package org.group10.payment.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.repository.PaymentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSeeder implements ApplicationRunner {

    private static final Long PAID_FIELD_ORDER_ID = 1L;
    private static final Long PAID_SEAT_ORDER_ID = 2L;
    private static final Long PENDING_SEAT_ORDER_ID = 3L;

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (paymentRepository.count() > 0) {
            log.info("PaymentSeeder: ya hay datos, se omite la carga");
            return;
        }

        paymentRepository.saveAll(List.of(
                approved(PAID_FIELD_ORDER_ID, PaymentMethod.CREDIT_CARD, "90000"),
                rejected(PAID_SEAT_ORDER_ID, PaymentMethod.DEBIT_CARD, "40000", "Fondos insuficientes"),
                approved(PAID_SEAT_ORDER_ID, PaymentMethod.ACCOUNT_MONEY, "40000"),
                pending(PENDING_SEAT_ORDER_ID, PaymentMethod.CREDIT_CARD, "70000")
        ));

        log.info("PaymentSeeder: {} pagos cargados", paymentRepository.count());
    }

    private static Payment approved(Long orderId, PaymentMethod method, String amount) {
        return payment(orderId, method, amount, PaymentStatus.APPROVED)
                .gatewayId(mockGatewayId())
                .build();
    }

    private static Payment rejected(Long orderId, PaymentMethod method, String amount, String failureReason) {
        return payment(orderId, method, amount, PaymentStatus.REJECTED)
                .gatewayId(mockGatewayId())
                .failureReason(failureReason)
                .build();
    }

    private static Payment pending(Long orderId, PaymentMethod method, String amount) {
        return payment(orderId, method, amount, PaymentStatus.PENDING).build();
    }

    private static Payment.PaymentBuilder payment(Long orderId, PaymentMethod method, String amount, PaymentStatus status) {
        return Payment.builder()
                .orderId(orderId)
                .idempotencyKey(UUID.randomUUID().toString())
                .paymentMethod(method)
                .amount(new BigDecimal(amount))
                .status(status);
    }

    private static String mockGatewayId() {
        return "MOCK-" + UUID.randomUUID();
    }
}
