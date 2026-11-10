package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.EventAvailabilityResponse;
import org.group10.ticket.dto.EventAvailabilityResponse.SeatAvailability;
import org.group10.ticket.dto.EventAvailabilityResponse.SectorAvailability;
import org.group10.ticket.entity.EventSeat;
import org.group10.ticket.entity.EventSectorPrice;
import org.group10.ticket.entity.EventSectorStock;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorPriceRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final EventSectorPriceRepository eventSectorPriceRepository;
    private final EventSectorStockRepository eventSectorStockRepository;
    private final EventSeatRepository eventSeatRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EventAvailabilityResponse findAvailability(Long eventId) {
        List<EventSectorPrice> prices = eventSectorPriceRepository.findByEventIdOrderByEventSectorId(eventId);
        if (prices.isEmpty()) {
            throw new TicketYaException(CommonErrorCode.RESOURCE_NOT_FOUND,
                    "El evento %d no tiene lugares a la venta".formatted(eventId));
        }

        Map<Long, EventSectorStock> stocks = eventSectorStockRepository.findByEventId(eventId).stream()
                .collect(Collectors.toMap(EventSectorStock::getEventSectorId, Function.identity()));
        Map<Long, List<EventSeat>> seatsBySector = eventSeatRepository.findByEventIdOrderBySeatId(eventId).stream()
                .collect(Collectors.groupingBy(EventSeat::getEventSectorId));
        LocalDateTime now = LocalDateTime.now(clock);

        return new EventAvailabilityResponse(eventId, prices.stream()
                .map(price -> availabilityOfSector(price, stocks.get(price.getEventSectorId()),
                        seatsBySector.getOrDefault(price.getEventSectorId(), List.of()), now))
                .toList());
    }

    private static SectorAvailability availabilityOfSector(EventSectorPrice price, EventSectorStock stock,
                                                           List<EventSeat> seats, LocalDateTime now) {
        if (stock != null) {
            return new SectorAvailability(price.getEventSectorId(), price.getPrice(), false,
                    stock.getCapacity() - stock.getTaken(), null);
        }
        List<SeatAvailability> seatAvailabilities = seats.stream()
                .map(seat -> new SeatAvailability(seat.getSeatId(), seat.isFreeAt(now)))
                .toList();
        int freeSeats = (int) seatAvailabilities.stream().filter(SeatAvailability::available).count();
        return new SectorAvailability(price.getEventSectorId(), price.getPrice(), true, freeSeats, seatAvailabilities);
    }
}
