package org.group10.ticket.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.EventSaleStatus;
import org.group10.ticket.entity.EventSeat;
import org.group10.ticket.entity.EventSeatId;
import org.group10.ticket.entity.EventSeatStatus;
import org.group10.ticket.entity.EventSectorPrice;
import org.group10.ticket.entity.EventSectorStock;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;
import org.group10.ticket.entity.ReturnRequest;
import org.group10.ticket.entity.ReturnRequestStatus;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorPriceRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.ReturnRequestRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketSeeder implements ApplicationRunner {

    private static final Long CLIENT_USER_ID = 1L;

    private static final Long CONCERT_EVENT_ID = 1L;
    private static final Long PLAY_EVENT_ID = 2L;

    private static final Long CONCERT_FIELD_SECTOR_ID = 1L;
    private static final Long CONCERT_STAND_SECTOR_ID = 2L;
    private static final Long PLAY_STALLS_SECTOR_ID = 3L;
    private static final Long PLAY_BALCONY_SECTOR_ID = 4L;

    private static final BigDecimal CONCERT_FIELD_PRICE = new BigDecimal("45000");
    private static final BigDecimal CONCERT_STAND_PRICE = new BigDecimal("70000");
    private static final BigDecimal PLAY_STALLS_PRICE = new BigDecimal("20000");
    private static final BigDecimal PLAY_BALCONY_PRICE = new BigDecimal("15000");

    private static final int CONCERT_FIELD_CAPACITY = 5000;

    private static final long STADIUM_FIRST_SEAT_ID = 1L;
    private static final long STADIUM_LAST_SEAT_ID = 80L;
    private static final long THEATER_FIRST_SEAT_ID = 81L;
    private static final long THEATER_LAST_STALLS_SEAT_ID = 130L;
    private static final long THEATER_FIRST_BALCONY_SEAT_ID = 131L;
    private static final long THEATER_LAST_SEAT_ID = 154L;

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final EventSaleRepository eventSaleRepository;
    private final EventSectorPriceRepository eventSectorPriceRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectorStockRepository eventSectorStockRepository;
    private final ReturnRequestRepository returnRequestRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (orderRepository.count() > 0) {
            log.info("TicketSeeder: ya hay datos, se omite la carga");
            return;
        }

        seedEventSales();
        seedInventory();
        seedPaidFieldOrder();
        seedPaidSeatOrder();
        seedPendingSeatOrder();

        log.info("TicketSeeder: {} asientos por evento, {} cupos, {} órdenes, {} entradas y {} devoluciones cargados",
                eventSeatRepository.count(), eventSectorStockRepository.count(), orderRepository.count(),
                ticketRepository.count(), returnRequestRepository.count());
    }

    private void seedEventSales() {
        LocalDateTime now = LocalDateTime.now();
        eventSaleRepository.saveAll(List.of(
                EventSale.builder()
                        .eventId(CONCERT_EVENT_ID)
                        .status(EventSaleStatus.PUBLISHED)
                        .dateTime(now.plusDays(33))
                        .saleStart(now.minusDays(2))
                        .highDemand(true)
                        .maxTicketsPerCustomer(4)
                        .build(),
                EventSale.builder()
                        .eventId(PLAY_EVENT_ID)
                        .status(EventSaleStatus.PUBLISHED)
                        .dateTime(now.plusDays(26))
                        .highDemand(false)
                        .maxTicketsPerCustomer(6)
                        .build()
        ));
        eventSectorPriceRepository.saveAll(List.of(
                sectorPrice(CONCERT_FIELD_SECTOR_ID, CONCERT_EVENT_ID, CONCERT_FIELD_PRICE),
                sectorPrice(CONCERT_STAND_SECTOR_ID, CONCERT_EVENT_ID, CONCERT_STAND_PRICE),
                sectorPrice(PLAY_STALLS_SECTOR_ID, PLAY_EVENT_ID, PLAY_STALLS_PRICE),
                sectorPrice(PLAY_BALCONY_SECTOR_ID, PLAY_EVENT_ID, PLAY_BALCONY_PRICE)
        ));
    }

    private void seedInventory() {
        eventSeatRepository.saveAll(availableSeats(CONCERT_EVENT_ID, CONCERT_STAND_SECTOR_ID,
                STADIUM_FIRST_SEAT_ID, STADIUM_LAST_SEAT_ID));
        eventSeatRepository.saveAll(availableSeats(PLAY_EVENT_ID, PLAY_STALLS_SECTOR_ID,
                THEATER_FIRST_SEAT_ID, THEATER_LAST_STALLS_SEAT_ID));
        eventSeatRepository.saveAll(availableSeats(PLAY_EVENT_ID, PLAY_BALCONY_SECTOR_ID,
                THEATER_FIRST_BALCONY_SEAT_ID, THEATER_LAST_SEAT_ID));
        eventSectorStockRepository.save(EventSectorStock.builder()
                .eventSectorId(CONCERT_FIELD_SECTOR_ID)
                .eventId(CONCERT_EVENT_ID)
                .capacity(CONCERT_FIELD_CAPACITY)
                .taken(0)
                .build());
    }

    private void seedPaidFieldOrder() {
        BigDecimal price = CONCERT_FIELD_PRICE;
        Order order = orderRepository.save(order(CONCERT_EVENT_ID, OrderStatus.PAID, price.multiply(BigDecimal.TWO))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build());

        ticketRepository.saveAll(List.of(
                issuedTicket(order, CONCERT_FIELD_SECTOR_ID, null, price),
                issuedTicket(order, CONCERT_FIELD_SECTOR_ID, null, price)
        ));

        EventSectorStock field = eventSectorStockRepository.findById(CONCERT_FIELD_SECTOR_ID).orElseThrow();
        field.setTaken(field.getTaken() + 2);
    }

    private void seedPaidSeatOrder() {
        BigDecimal price = PLAY_STALLS_PRICE;
        Order order = orderRepository.save(order(PLAY_EVENT_ID, OrderStatus.PAID, price.multiply(BigDecimal.TWO))
                .paymentMethod(PaymentMethod.ACCOUNT_MONEY)
                .build());

        List<Long> seatIds = List.of(THEATER_FIRST_SEAT_ID, THEATER_FIRST_SEAT_ID + 1);
        List<Ticket> tickets = ticketRepository.saveAll(seatIds.stream()
                .map(seatId -> issuedTicket(order, PLAY_STALLS_SECTOR_ID, seatId, price))
                .toList());
        seatIds.forEach(seatId -> takeSeat(PLAY_EVENT_ID, seatId, order, EventSeatStatus.SOLD, null));

        returnRequestRepository.save(ReturnRequest.builder()
                .ticket(tickets.getLast())
                .requestedBy(CLIENT_USER_ID)
                .requestedAt(LocalDateTime.now())
                .status(ReturnRequestStatus.PENDING)
                .build());
    }

    private void seedPendingSeatOrder() {
        BigDecimal price = CONCERT_STAND_PRICE;
        Order order = orderRepository.save(order(CONCERT_EVENT_ID, OrderStatus.PENDING, price)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentAttemptKey("seed-pending-order-3")
                .build());

        ticketRepository.save(Ticket.builder()
                .order(order)
                .eventSectorId(CONCERT_STAND_SECTOR_ID)
                .seatId(STADIUM_FIRST_SEAT_ID)
                .price(price)
                .status(TicketStatus.RESERVED)
                .build());
        takeSeat(CONCERT_EVENT_ID, STADIUM_FIRST_SEAT_ID, order, EventSeatStatus.RESERVED, order.getExpiresAt());
    }

    private void takeSeat(Long eventId, Long seatId, Order order, EventSeatStatus status, LocalDateTime reservedUntil) {
        EventSeat seat = eventSeatRepository.findById(new EventSeatId(eventId, seatId)).orElseThrow();
        seat.setStatus(status);
        seat.setOrder(order);
        seat.setReservedUntil(reservedUntil);
    }

    private static List<EventSeat> availableSeats(Long eventId, Long eventSectorId, long firstSeatId, long lastSeatId) {
        return LongStream.rangeClosed(firstSeatId, lastSeatId)
                .mapToObj(seatId -> EventSeat.builder()
                        .eventId(eventId)
                        .seatId(seatId)
                        .eventSectorId(eventSectorId)
                        .status(EventSeatStatus.AVAILABLE)
                        .build())
                .toList();
    }

    private static EventSectorPrice sectorPrice(Long eventSectorId, Long eventId, BigDecimal price) {
        return EventSectorPrice.builder().eventSectorId(eventSectorId).eventId(eventId).price(price).build();
    }

    private static Order.OrderBuilder order(Long eventId, OrderStatus status, BigDecimal total) {
        return Order.builder()
                .userId(CLIENT_USER_ID)
                .eventId(eventId)
                .status(status)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .total(total)
                .idempotencyKey(UUID.randomUUID().toString());
    }

    private static Ticket issuedTicket(Order order, Long eventSectorId, Long seatId, BigDecimal price) {
        return Ticket.builder()
                .order(order)
                .eventSectorId(eventSectorId)
                .seatId(seatId)
                .price(price)
                .qrCode(UUID.randomUUID().toString())
                .status(TicketStatus.ISSUED)
                .build();
    }
}
