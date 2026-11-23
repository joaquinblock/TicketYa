package org.group10.recommendation.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Eventos para los que el cliente compró entradas, armado con los {@code purchase.confirmed} de ticket-service.
 * Un documento por cliente. Cada evento está una sola vez, aunque haya comprado varias veces o el mensaje se repita.
 * {@code userId} es el id del auth-service y {@code eventIds} son ids del event-service (RFC-004).
 */
@Document(collection = "purchase_history")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PurchaseHistory {

    @Id
    private Long userId;

    private List<Long> eventIds;
}
