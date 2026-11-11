package org.group10.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "event_seat")
@IdClass(EventSeatId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventSeat {

    @Id
    private Long eventId;

    @Id
    private Long seatId;

    @Column(nullable = false)
    private Long eventSectorId;

    @Column(name = "seat_row", nullable = false)
    private String row;

    @Column(nullable = false)
    private Integer number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSeatStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    private LocalDateTime reservedUntil;

    @Version
    private Long version;

    public boolean isFreeAt(LocalDateTime moment) {
        return status == EventSeatStatus.AVAILABLE
                || (status == EventSeatStatus.RESERVED && !reservedUntil.isAfter(moment));
    }

    public void holdFor(Order reservingOrder) {
        status = EventSeatStatus.RESERVED;
        order = reservingOrder;
        reservedUntil = reservingOrder.getExpiresAt();
    }
}
