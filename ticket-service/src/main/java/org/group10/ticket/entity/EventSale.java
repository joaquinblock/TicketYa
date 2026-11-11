package org.group10.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_sale")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventSale {

    @Id
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSaleStatus status;

    @Column(nullable = false)
    private String eventName;

    @Column(nullable = false)
    private String venueName;

    @Column(nullable = false)
    private String venueAddress;

    @Column(nullable = false)
    private String venueCity;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    private LocalDateTime saleStart;

    @Column(nullable = false)
    private boolean highDemand;

    private Integer maxTicketsPerCustomer;

    @Column(nullable = false)
    private boolean availabilityNotificationPending;

    public boolean hasAlreadyStartedAt(LocalDateTime moment) {
        return !dateTime.isAfter(moment);
    }

    public boolean hasNotOpenedAt(LocalDateTime moment) {
        return saleStart != null && saleStart.isAfter(moment);
    }

    public boolean allowsToAddTickets(long ticketsOfTheCustomer, int requestedTickets) {
        return maxTicketsPerCustomer == null || ticketsOfTheCustomer + requestedTickets <= maxTicketsPerCustomer;
    }
}
