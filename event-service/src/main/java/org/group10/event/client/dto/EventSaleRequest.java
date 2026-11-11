package org.group10.event.client.dto;

import org.group10.event.entity.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EventSaleRequest(
        EventStatus status,
        String eventName,
        String venueName,
        String venueAddress,
        String venueCity,
        LocalDateTime dateTime,
        LocalDateTime saleStart,
        boolean highDemand,
        Integer maxTicketsPerCustomer,
        List<SectorSale> sectors
) {

    public record SectorSale(Long eventSectorId, String sectorName, BigDecimal price, Integer capacity,
                             List<SeatSale> seats) {
    }

    public record SeatSale(Long seatId, String row, Integer number) {
    }
}
