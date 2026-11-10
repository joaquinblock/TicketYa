package org.group10.ticket.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.ticket.dto.EventSaleRequest;
import org.group10.ticket.dto.EventSaleRequest.SeatSale;
import org.group10.ticket.dto.EventSaleRequest.SectorSale;
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
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorPriceRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.ReturnRequestRepository;
import org.group10.ticket.repository.TicketRepository;
import org.group10.ticket.service.EventSaleService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketSeeder implements ApplicationRunner {

    private static final Long CLIENT_USER_ID = 1L;

    private static final Long CONCERT_EVENT_ID = 1L;
    private static final Long PLAY_EVENT_ID = 2L;
    private static final Long SOLD_OUT_JAZZ_EVENT_ID = 6L;

    private static final Long CONCERT_FIELD_SECTOR_ID = 1L;
    private static final Long CONCERT_STAND_SECTOR_ID = 2L;
    private static final Long PLAY_STALLS_SECTOR_ID = 3L;
    private static final Long JAZZ_ROOM_SECTOR_ID = 10L;

    private static final long STADIUM_FIRST_SEAT_ID = 1L;
    private static final long THEATER_FIRST_SEAT_ID = 81L;

    private static final Venue STADIUM = new Venue("Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires");
    private static final Venue THEATER = new Venue("Teatro de la Sierra", "Calle Pinto 650", "Tandil");
    private static final Venue ARENA = new Venue("Arena Córdoba", "Av. Cárcano 1500", "Córdoba");
    private static final Venue LITORAL = new Venue("Estadio del Litoral", "Av. Colón 2200", "Rosario");
    private static final Venue HARBOR_HALL = new Venue("Sala del Puerto", "Av. Martínez de Hoz 4100", "Mar del Plata");

    private static final SeatBlock STADIUM_STAND = new SeatBlock(1L, List.of("A", "B", "C", "D"), 20);
    private static final SeatBlock THEATER_STALLS = new SeatBlock(81L, List.of("A", "B", "C", "D", "E"), 10);
    private static final SeatBlock THEATER_BALCONY = new SeatBlock(131L, List.of("A", "B", "C"), 8);
    private static final SeatBlock ARENA_LOWER_STAND = new SeatBlock(155L, List.of("A", "B", "C"), 15);
    private static final SeatBlock LITORAL_STAND = new SeatBlock(200L, List.of("A", "B"), 20);

    private final EventSaleService eventSaleService;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final EventSectorPriceRepository eventSectorPriceRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectorStockRepository eventSectorStockRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final Clock clock;

    private record Venue(String name, String address, String city) {
    }

    private record SeatBlock(long firstSeatId, List<String> rows, int seatsPerRow) {

        List<SeatSale> seats() {
            AtomicLong nextSeatId = new AtomicLong(firstSeatId);
            return rows.stream()
                    .flatMap(row -> IntStream.rangeClosed(1, seatsPerRow)
                            .mapToObj(number -> new SeatSale(nextSeatId.getAndIncrement(), row, number)))
                    .toList();
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (orderRepository.count() > 0) {
            log.info("TicketSeeder: ya hay datos, se omite la carga");
            return;
        }

        seedEventSalesWithTheSameDataAsTheEventSeeder();
        sellOutTheJazzRoom();
        seedPaidFieldOrder();
        seedPaidSeatOrder();
        seedPendingSeatOrder();

        log.info("TicketSeeder: {} asientos por evento, {} cupos, {} órdenes, {} entradas y {} devoluciones cargados",
                eventSeatRepository.count(), eventSectorStockRepository.count(), orderRepository.count(),
                ticketRepository.count(), returnRequestRepository.count());
    }

    private void seedEventSalesWithTheSameDataAsTheEventSeeder() {
        sale(CONCERT_EVENT_ID, "Los Serranos en vivo", STADIUM, EventSaleStatus.PUBLISHED, at(33, 21, 0), at(-2, 10, 0), true, 4,
                generalAdmission(CONCERT_FIELD_SECTOR_ID, "Campo", "45000", 5000),
                numbered(CONCERT_STAND_SECTOR_ID, "Platea", "70000", STADIUM_STAND));
        sale(PLAY_EVENT_ID, "La casa de las sierras", THEATER, EventSaleStatus.PUBLISHED, at(26, 20, 30), null, false, 6,
                numbered(PLAY_STALLS_SECTOR_ID, "Platea", "20000", THEATER_STALLS),
                numbered(4L, "Pullman", "15000", THEATER_BALCONY));
        sale(4L, "Festival de Verano", ARENA, EventSaleStatus.PUBLISHED, at(60, 18, 0), at(20, 12, 0), true, 6,
                generalAdmission(6L, "Campo", "38000", 8000),
                numbered(7L, "Platea Baja", "55000", ARENA_LOWER_STAND));
        sale(5L, "Central vs. Unión", LITORAL, EventSaleStatus.PUBLISHED, at(15, 17, 0), null, false, 4,
                generalAdmission(8L, "Popular", "12000", 12000),
                numbered(9L, "Platea", "25000", LITORAL_STAND));
        sale(SOLD_OUT_JAZZ_EVENT_ID, "Jazz en el puerto", HARBOR_HALL, EventSaleStatus.SOLD_OUT, at(10, 22, 0), null, false, 2,
                generalAdmission(JAZZ_ROOM_SECTOR_ID, "Sala", "18000", 300));
        sale(7L, "Los Serranos en Rosario", LITORAL, EventSaleStatus.SUSPENDED, at(40, 21, 0), null, false, 4,
                generalAdmission(11L, "Popular", "30000", 12000),
                numbered(12L, "Platea", "48000", LITORAL_STAND));
        sale(8L, "Recital acústico", ARENA, EventSaleStatus.CANCELLED, at(20, 21, 30), null, false, null,
                numbered(13L, "Platea Baja", "35000", ARENA_LOWER_STAND));
        sale(9L, "Jornada de arquitectura de software", HARBOR_HALL, EventSaleStatus.FINISHED, at(-30, 9, 0), null,
                false, null,
                generalAdmission(14L, "Sala", "8000", 300));
        sale(11L, "Peña folklórica serrana", THEATER, EventSaleStatus.PUBLISHED, at(18, 21, 0), null, false, 8,
                numbered(15L, "Platea", "9000", THEATER_STALLS),
                numbered(16L, "Pullman", "7000", THEATER_BALCONY));
    }

    private void sale(Long eventId, String eventName, Venue venue, EventSaleStatus status, LocalDateTime dateTime,
                      LocalDateTime saleStart, boolean highDemand, Integer maxTicketsPerCustomer,
                      SectorSale... sectors) {
        eventSaleService.synchronizeEventSale(eventId, new EventSaleRequest(status, eventName, venue.name(),
                venue.address(), venue.city(), dateTime, saleStart, highDemand, maxTicketsPerCustomer,
                List.of(sectors)));
    }

    private static SectorSale generalAdmission(Long eventSectorId, String name, String price, int capacity) {
        return new SectorSale(eventSectorId, name, new BigDecimal(price), capacity, null);
    }

    private static SectorSale numbered(Long eventSectorId, String name, String price, SeatBlock seats) {
        return new SectorSale(eventSectorId, name, new BigDecimal(price), null, seats.seats());
    }

    private LocalDateTime at(int daysFromToday, int hour, int minute) {
        return LocalDate.now(clock).plusDays(daysFromToday).atTime(LocalTime.of(hour, minute));
    }

    private void sellOutTheJazzRoom() {
        EventSectorStock room = eventSectorStockRepository.findById(JAZZ_ROOM_SECTOR_ID).orElseThrow();
        room.setTaken(room.getCapacity());
    }

    private void seedPaidFieldOrder() {
        EventSectorPrice field = sectorOf(CONCERT_FIELD_SECTOR_ID);
        Order order = orderRepository.save(order(CONCERT_EVENT_ID, OrderStatus.PAID,
                field.getPrice().multiply(BigDecimal.TWO))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build());

        ticketRepository.saveAll(List.of(
                issuedTicket(order, field).build(),
                issuedTicket(order, field).build()
        ));

        EventSectorStock stock = eventSectorStockRepository.findById(CONCERT_FIELD_SECTOR_ID).orElseThrow();
        stock.setTaken(stock.getTaken() + 2);
    }

    private void seedPaidSeatOrder() {
        EventSectorPrice stalls = sectorOf(PLAY_STALLS_SECTOR_ID);
        Order order = orderRepository.save(order(PLAY_EVENT_ID, OrderStatus.PAID,
                stalls.getPrice().multiply(BigDecimal.TWO))
                .paymentMethod(PaymentMethod.ACCOUNT_MONEY)
                .build());

        List<EventSeat> seats = List.of(seatOf(PLAY_EVENT_ID, THEATER_FIRST_SEAT_ID),
                seatOf(PLAY_EVENT_ID, THEATER_FIRST_SEAT_ID + 1));
        List<Ticket> tickets = ticketRepository.saveAll(seats.stream()
                .map(seat -> issuedTicket(order, stalls)
                        .seatId(seat.getSeatId())
                        .row(seat.getRow())
                        .number(seat.getNumber())
                        .build())
                .toList());
        seats.forEach(seat -> takeSeat(seat, order, EventSeatStatus.SOLD, null));

        returnRequestRepository.save(ReturnRequest.builder()
                .ticket(tickets.getLast())
                .requestedBy(CLIENT_USER_ID)
                .requestedAt(LocalDateTime.now(clock))
                .status(ReturnRequestStatus.PENDING)
                .build());
    }

    private void seedPendingSeatOrder() {
        EventSectorPrice stand = sectorOf(CONCERT_STAND_SECTOR_ID);
        Order order = orderRepository.save(order(CONCERT_EVENT_ID, OrderStatus.PENDING, stand.getPrice())
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentAttemptKey("seed-pending-order-3")
                .build());

        EventSeat seat = seatOf(CONCERT_EVENT_ID, STADIUM_FIRST_SEAT_ID);
        ticketRepository.save(Ticket.builder()
                .order(order)
                .eventSectorId(stand.getEventSectorId())
                .sectorName(stand.getSectorName())
                .seatId(seat.getSeatId())
                .row(seat.getRow())
                .number(seat.getNumber())
                .price(stand.getPrice())
                .status(TicketStatus.RESERVED)
                .build());
        takeSeat(seat, order, EventSeatStatus.RESERVED, order.getExpiresAt());
    }

    private EventSectorPrice sectorOf(Long eventSectorId) {
        return eventSectorPriceRepository.findById(eventSectorId).orElseThrow();
    }

    private EventSeat seatOf(Long eventId, Long seatId) {
        return eventSeatRepository.findById(new EventSeatId(eventId, seatId)).orElseThrow();
    }

    private static void takeSeat(EventSeat seat, Order order, EventSeatStatus status, LocalDateTime reservedUntil) {
        seat.setStatus(status);
        seat.setOrder(order);
        seat.setReservedUntil(reservedUntil);
    }

    private Order.OrderBuilder order(Long eventId, OrderStatus status, BigDecimal total) {
        return Order.builder()
                .userId(CLIENT_USER_ID)
                .eventId(eventId)
                .status(status)
                .expiresAt(LocalDateTime.now(clock).plusMinutes(10))
                .total(total)
                .idempotencyKey(UUID.randomUUID().toString());
    }

    private static Ticket.TicketBuilder issuedTicket(Order order, EventSectorPrice sector) {
        return Ticket.builder()
                .order(order)
                .eventSectorId(sector.getEventSectorId())
                .sectorName(sector.getSectorName())
                .price(sector.getPrice())
                .qrCode(UUID.randomUUID().toString())
                .status(TicketStatus.ISSUED);
    }
}
