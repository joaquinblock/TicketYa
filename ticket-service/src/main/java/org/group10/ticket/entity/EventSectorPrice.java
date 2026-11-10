package org.group10.ticket.entity;

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

import java.math.BigDecimal;

@Entity
@Table(name = "event_sector_price")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventSectorPrice {

    @Id
    private Long eventSectorId;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private String sectorName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}
