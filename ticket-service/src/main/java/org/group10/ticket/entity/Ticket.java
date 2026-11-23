package org.group10.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long eventSectorId;

    @Column(nullable = false)
    private String sectorName;

    private Long seatId;

    @Column(name = "seat_row")
    private String row;

    private Integer number;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(unique = true)
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    private LocalDateTime usedAt;

    private Long validatedBy;

    /**
     * Cuándo payment-service aceptó el reintegro de una entrada que no llegó a venderse porque la compra falló
     * después de cobrar (US-TKT-22). La entrada sigue {@code VOIDED}: no es una devolución.
     */
    private LocalDateTime compensatedAt;

    /**
     * Por qué payment-service rechazó el reintegro de compensación con un 4xx. Reintentar no lo arregla, así que el
     * job deja de pedirlo y queda para resolver a mano.
     */
    private String refundError;

    @Version
    private Long version;

    public void issue() {
        status = TicketStatus.ISSUED;
        qrCode = UUID.randomUUID().toString();
    }
}
