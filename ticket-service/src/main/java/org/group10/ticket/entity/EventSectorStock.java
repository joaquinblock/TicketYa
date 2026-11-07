package org.group10.ticket.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "event_sector_stock",
        check = @CheckConstraint(name = "ck_event_sector_stock_taken", constraint = "taken >= 0 AND taken <= capacity")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventSectorStock {

    @Id
    private Long eventSectorId;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer taken;
}
