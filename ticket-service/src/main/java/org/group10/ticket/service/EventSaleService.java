package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import org.group10.ticket.dto.EventSaleRequest;
import org.group10.ticket.dto.EventSaleRequest.SectorSale;
import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.EventSaleStatus;
import org.group10.ticket.entity.EventSeat;
import org.group10.ticket.entity.EventSeatStatus;
import org.group10.ticket.entity.EventSectorPrice;
import org.group10.ticket.entity.EventSectorStock;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorPriceRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventSaleService {

    private final EventSaleRepository eventSaleRepository;
    private final EventSectorPriceRepository eventSectorPriceRepository;
    private final EventSectorStockRepository eventSectorStockRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventCancellationService eventCancellationService;

    @Transactional
    public void synchronizeEventSale(Long eventId, EventSaleRequest request) {
        eventSaleRepository.save(saleOf(eventId, request));
        eventSectorPriceRepository.saveAll(request.sectors().stream()
                .map(sector -> priceOf(eventId, sector))
                .toList());
        createMissingSectorStocks(eventId, request.sectors());
        createMissingSeats(eventId, request.sectors());

        if (request.status() == EventSaleStatus.CANCELLED) {
            eventCancellationService.closeOpenPurchasesOfTheEvent(eventId);
        }
    }

    private void createMissingSectorStocks(Long eventId, List<SectorSale> sectors) {
        List<SectorSale> sectorsWithCapacity = sectors.stream().filter(sector -> sector.capacity() != null).toList();
        Set<Long> existingStocks = eventSectorStockRepository
                .findAllById(sectorsWithCapacity.stream().map(SectorSale::eventSectorId).toList())
                .stream()
                .map(EventSectorStock::getEventSectorId)
                .collect(Collectors.toSet());

        eventSectorStockRepository.saveAll(sectorsWithCapacity.stream()
                .filter(sector -> !existingStocks.contains(sector.eventSectorId()))
                .map(sector -> emptyStockOf(eventId, sector))
                .toList());
    }

    private void createMissingSeats(Long eventId, List<SectorSale> sectors) {
        Set<Long> existingSeats = eventSeatRepository.findSeatIdsOfEvent(eventId);

        eventSeatRepository.saveAll(sectors.stream()
                .filter(sector -> sector.seatIds() != null)
                .flatMap(sector -> sector.seatIds().stream()
                        .filter(seatId -> !existingSeats.contains(seatId))
                        .map(seatId -> availableSeat(eventId, sector.eventSectorId(), seatId)))
                .toList());
    }

    private static EventSale saleOf(Long eventId, EventSaleRequest request) {
        return EventSale.builder()
                .eventId(eventId)
                .status(request.status())
                .dateTime(request.dateTime())
                .saleStart(request.saleStart())
                .highDemand(request.highDemand())
                .maxTicketsPerCustomer(request.maxTicketsPerCustomer())
                .build();
    }

    private static EventSectorPrice priceOf(Long eventId, SectorSale sector) {
        return EventSectorPrice.builder()
                .eventSectorId(sector.eventSectorId())
                .eventId(eventId)
                .price(sector.price())
                .build();
    }

    private static EventSectorStock emptyStockOf(Long eventId, SectorSale sector) {
        return EventSectorStock.builder()
                .eventSectorId(sector.eventSectorId())
                .eventId(eventId)
                .capacity(sector.capacity())
                .taken(0)
                .build();
    }

    private static EventSeat availableSeat(Long eventId, Long eventSectorId, Long seatId) {
        return EventSeat.builder()
                .eventId(eventId)
                .seatId(seatId)
                .eventSectorId(eventSectorId)
                .status(EventSeatStatus.AVAILABLE)
                .build();
    }
}
