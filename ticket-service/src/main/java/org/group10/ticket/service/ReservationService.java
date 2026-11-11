package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.ErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.config.ReservationProperties;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.dto.ReservationRequest;
import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.EventSaleStatus;
import org.group10.ticket.entity.EventSeat;
import org.group10.ticket.entity.EventSectorPrice;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorPriceRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final List<TicketStatus> STATUSES_THAT_COUNT_FOR_THE_LIMIT =
            List.of(TicketStatus.RESERVED, TicketStatus.ISSUED, TicketStatus.USED);

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final EventSaleRepository eventSaleRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectorStockRepository eventSectorStockRepository;
    private final EventSectorPriceRepository eventSectorPriceRepository;
    private final TransactionOperations transactions;
    private final OrderQueryService orderQueryService;
    private final ReservationProperties reservationProperties;
    private final Clock clock;

    public OrderResponse reserve(Long userId, ReservationRequest request) {
        return findOrderOfTheAttempt(userId, request)
                .orElseGet(() -> reserveUnlessTheSameAttemptWonTheRace(userId, request));
    }

    private Optional<OrderResponse> findOrderOfTheAttempt(Long userId, ReservationRequest request) {
        return orderRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(existingOrder -> answerWithTheOrderOfTheAttempt(existingOrder, userId, request));
    }

    private OrderResponse reserveUnlessTheSameAttemptWonTheRace(Long userId, ReservationRequest request) {
        try {
            return reserveNewOrder(userId, request);
        } catch (RuntimeException failure) {
            return findOrderOfTheAttempt(userId, request).orElseThrow(() -> rejectionFor(failure, request));
        }
    }

    private OrderResponse reserveNewOrder(Long userId, ReservationRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        EventSale sale = findSaleOfEvent(request.eventId());
        ensureSaleIsOpen(sale, now);
        ensureCustomerStaysWithinTheLimit(sale, userId, request);

        return transactions.execute(transaction -> request.choosesSeats()
                ? holdChosenSeats(userId, request, now)
                : takeSectorPlaces(userId, request, now));
    }

    private static RuntimeException rejectionFor(RuntimeException failure, ReservationRequest request) {
        return failure instanceof ConcurrencyFailureException ? placesAlreadyTaken(request) : failure;
    }

    private OrderResponse answerWithTheOrderOfTheAttempt(Order order, Long userId, ReservationRequest request) {
        List<Ticket> tickets = ticketRepository.findByOrderIdOrderById(order.getId());
        if (!order.getUserId().equals(userId) || !repeatsTheReservation(order, tickets, request)) {
            throw new TicketYaException(CommonErrorCode.DATA_CONFLICT,
                    "La clave de idempotencia ya se usó con otros datos");
        }
        return orderQueryService.toResponse(order, tickets);
    }

    private static boolean repeatsTheReservation(Order order, List<Ticket> tickets, ReservationRequest request) {
        if (!order.getEventId().equals(request.eventId())) {
            return false;
        }
        if (request.choosesSeats()) {
            Set<Long> reservedSeats = tickets.stream().map(Ticket::getSeatId).collect(Collectors.toSet());
            return reservedSeats.equals(new HashSet<>(request.seatIds()));
        }
        return tickets.size() == request.quantity() && tickets.stream()
                .allMatch(ticket -> ticket.getSeatId() == null
                        && ticket.getEventSectorId().equals(request.eventSectorId()));
    }

    private EventSale findSaleOfEvent(Long eventId) {
        return eventSaleRepository.findById(eventId)
                .orElseThrow(() -> new TicketYaException(CommonErrorCode.RESOURCE_NOT_FOUND,
                        "El evento %d no tiene lugares a la venta".formatted(eventId)));
    }

    private static void ensureSaleIsOpen(EventSale sale, LocalDateTime now) {
        if (sale.getStatus() != EventSaleStatus.PUBLISHED || sale.hasAlreadyStartedAt(now)) {
            throw new TicketYaException(TicketErrorCode.EVENT_NOT_ON_SALE,
                    "El evento %d no está a la venta".formatted(sale.getEventId()));
        }
        if (sale.hasNotOpenedAt(now)) {
            throw new TicketYaException(TicketErrorCode.SALE_NOT_STARTED,
                    "La venta del evento %d abre el %s".formatted(sale.getEventId(), sale.getSaleStart()));
        }
    }

    private void ensureCustomerStaysWithinTheLimit(EventSale sale, Long userId, ReservationRequest request) {
        long ticketsOfTheCustomer = ticketRepository.countTicketsOfCustomerInEvent(
                userId, request.eventId(), STATUSES_THAT_COUNT_FOR_THE_LIMIT);
        if (!sale.allowsToAddTickets(ticketsOfTheCustomer, request.requestedTickets())) {
            throw new TicketYaException(TicketErrorCode.TICKET_LIMIT_EXCEEDED,
                    "Se pueden comprar hasta %d entradas para este evento".formatted(sale.getMaxTicketsPerCustomer()));
        }
    }

    private OrderResponse holdChosenSeats(Long userId, ReservationRequest request, LocalDateTime now) {
        List<EventSeat> seats = findChosenSeats(request);
        if (!seats.stream().allMatch(seat -> seat.isFreeAt(now))) {
            throw placesAlreadyTaken(request);
        }

        Map<Long, EventSectorPrice> sectors = sectorsOfEvent(request.eventId());
        BigDecimal total = seats.stream()
                .map(seat -> sectors.get(seat.getEventSectorId()).getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = orderRepository.save(pendingOrder(userId, request, now, total));
        seats.forEach(seat -> seat.holdFor(order));

        return orderQueryService.toResponse(order, ticketRepository.saveAll(seats.stream()
                .map(seat -> reservedTicketForSeat(order, sectors.get(seat.getEventSectorId()), seat))
                .toList()));
    }

    private List<EventSeat> findChosenSeats(ReservationRequest request) {
        List<EventSeat> seats = eventSeatRepository.findByEventIdAndSeatIdInOrderBySeatId(
                request.eventId(), request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            Set<Long> foundSeats = seats.stream().map(EventSeat::getSeatId).collect(Collectors.toSet());
            List<Long> missingSeats = request.seatIds().stream().filter(seatId -> !foundSeats.contains(seatId)).toList();
            throw new TicketYaException(CommonErrorCode.RESOURCE_NOT_FOUND,
                    "Los asientos %s no existen en el evento %d".formatted(missingSeats, request.eventId()),
                    Map.of("missingSeatIds", missingSeats));
        }
        return seats;
    }

    private OrderResponse takeSectorPlaces(Long userId, ReservationRequest request, LocalDateTime now) {
        ensureSectorHasStockInTheEvent(request);
        if (eventSectorStockRepository.takePlacesIfTheyFit(request.eventSectorId(), request.quantity()) == 0) {
            throw placesAlreadyTaken(request);
        }

        EventSectorPrice sector = sectorsOfEvent(request.eventId()).get(request.eventSectorId());
        Order order = orderRepository.save(pendingOrder(userId, request, now,
                sector.getPrice().multiply(BigDecimal.valueOf(request.quantity()))));

        return orderQueryService.toResponse(order, ticketRepository.saveAll(IntStream.range(0, request.quantity())
                .mapToObj(place -> reservedTicketWithoutSeat(order, sector))
                .toList()));
    }

    private void ensureSectorHasStockInTheEvent(ReservationRequest request) {
        eventSectorStockRepository.findById(request.eventSectorId())
                .filter(stock -> stock.getEventId().equals(request.eventId()))
                .orElseThrow(() -> new TicketYaException(CommonErrorCode.RESOURCE_NOT_FOUND,
                        "El sector %d no es un sector sin numerar del evento %d"
                                .formatted(request.eventSectorId(), request.eventId())));
    }

    private Map<Long, EventSectorPrice> sectorsOfEvent(Long eventId) {
        return eventSectorPriceRepository.findByEventIdOrderByEventSectorId(eventId).stream()
                .collect(Collectors.toMap(EventSectorPrice::getEventSectorId, Function.identity()));
    }

    private Order pendingOrder(Long userId, ReservationRequest request, LocalDateTime now, BigDecimal total) {
        return Order.builder()
                .userId(userId)
                .eventId(request.eventId())
                .status(OrderStatus.PENDING)
                .expiresAt(now.plus(reservationProperties.hold()))
                .total(total)
                .idempotencyKey(request.idempotencyKey())
                .build();
    }

    private static Ticket reservedTicketForSeat(Order order, EventSectorPrice sector, EventSeat seat) {
        return reservedTicketOf(order, sector)
                .seatId(seat.getSeatId())
                .row(seat.getRow())
                .number(seat.getNumber())
                .build();
    }

    private static Ticket reservedTicketWithoutSeat(Order order, EventSectorPrice sector) {
        return reservedTicketOf(order, sector).build();
    }

    private static Ticket.TicketBuilder reservedTicketOf(Order order, EventSectorPrice sector) {
        return Ticket.builder()
                .order(order)
                .eventSectorId(sector.getEventSectorId())
                .sectorName(sector.getSectorName())
                .price(sector.getPrice())
                .status(TicketStatus.RESERVED);
    }

    private static TicketYaException placesAlreadyTaken(ReservationRequest request) {
        ErrorCode errorCode = request.choosesSeats() ? TicketErrorCode.SEAT_UNAVAILABLE : TicketErrorCode.SECTOR_SOLD_OUT;
        String message = request.choosesSeats()
                ? "Alguno de los asientos elegidos ya no está disponible"
                : "No quedan lugares suficientes en el sector";
        return new TicketYaException(errorCode, message);
    }
}
