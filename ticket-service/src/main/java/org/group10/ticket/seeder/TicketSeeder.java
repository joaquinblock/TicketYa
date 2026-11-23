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
import java.util.function.Predicate;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketSeeder implements ApplicationRunner {

    private static final Long CLIENT_USER_ID = 1L;

    private static final Long CONCERT_EVENT_ID = 1L;
    private static final Long PLAY_EVENT_ID = 2L;
    private static final Long SOLD_OUT_JAZZ_EVENT_ID = 6L;
    private static final Long FINISHED_TALK_EVENT_ID = 9L;
    private static final Long FOLK_NIGHT_EVENT_ID = 11L;

    private static final Long CONCERT_FIELD_SECTOR_ID = 1L;
    private static final Long CONCERT_STAND_SECTOR_ID = 2L;
    private static final Long PLAY_STALLS_SECTOR_ID = 3L;
    private static final Long JAZZ_ROOM_SECTOR_ID = 10L;
    private static final Long FINISHED_TALK_SECTOR_ID = 14L;
    private static final Long FOLK_NIGHT_STALLS_SECTOR_ID = 15L;

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

    private static final BillboardSector STADIUM_FIELD = BillboardSector.generalAdmission(STADIUM, "Campo", 5000);
    private static final BillboardSector LITORAL_TERRACE = BillboardSector.generalAdmission(LITORAL, "Popular", 12000);
    private static final BillboardSector ARENA_FIELD = BillboardSector.generalAdmission(ARENA, "Campo", 8000);
    private static final BillboardSector HARBOR_ROOM = BillboardSector.generalAdmission(HARBOR_HALL, "Sala", 300);
    private static final BillboardSector THEATER_STALLS_SECTOR =
            BillboardSector.numbered(THEATER, "Platea", THEATER_STALLS);
    private static final BillboardSector THEATER_BALCONY_SECTOR =
            BillboardSector.numbered(THEATER, "Pullman", THEATER_BALCONY);

    /** The EventSeeder's billboard: events 13 to 42, each with a single sector (event_sector 18 to 47). */
    private static final long BILLBOARD_FIRST_EVENT_ID = 13L;
    private static final long BILLBOARD_FIRST_EVENT_SECTOR_ID = 18L;
    private static final int BILLBOARD_FIRST_DAY = 75;
    private static final int BILLBOARD_DAYS_APART = 3;
    private static final int BILLBOARD_BASE_PRICE = 50000;
    private static final int BILLBOARD_PRICE_STEP = 1500;
    private static final int BILLBOARD_MAX_TICKETS = 4;

    /** The EventSeeder's sale showcase: events 43 to 46 at the Estadio Ribera, event_sector 48 to 61. */
    private static final Venue RIBERA = new Venue("Estadio Ribera", "Av. Costanera 500", "La Plata");
    private static final SeatBlock RIBERA_LOWER_STAND = new SeatBlock(240L, List.of("A", "B", "C", "D", "E", "F", "G", "H"), 14);
    private static final SeatBlock RIBERA_UPPER_STAND = new SeatBlock(352L, List.of("A", "B", "C", "D"), 14);
    private static final SeatBlock RIBERA_VIP_BOX = new SeatBlock(408L, List.of("A", "B"), 6);
    private static final int RIBERA_FIELD_CAPACITY = 5000;
    private static final int SHOWCASE_MAX_TICKETS = 4;

    private static final Long BIG_NIGHT_EVENT_ID = 43L;
    private static final Long DERBY_EVENT_ID = 44L;
    private static final Long COAST_FESTIVAL_EVENT_ID = 45L;
    private static final Long CUP_FINAL_EVENT_ID = 46L;

    private static final Long BIG_NIGHT_LOWER_STAND_SECTOR_ID = 49L;
    private static final Long BIG_NIGHT_UPPER_STAND_SECTOR_ID = 50L;
    private static final Long DERBY_FIELD_SECTOR_ID = 52L;
    private static final Long COAST_FESTIVAL_FIELD_SECTOR_ID = 56L;
    private static final Long CUP_FINAL_LOWER_STAND_SECTOR_ID = 60L;
    private static final Long CUP_FINAL_VIP_BOX_SECTOR_ID = 61L;

    /** D7 of the Platea baja: the only seat left in the cup final. */
    private static final Long CUP_FINAL_LAST_FREE_SEAT_ID = 288L;
    /** In the big night, one Platea baja seat out of three is sold, scattered along the rows. */
    private static final int BIG_NIGHT_SOLD_EVERY = 3;

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

    /** A venue's sector as the billboard sells it: a capacity for general admission, seats when numbered. */
    private record BillboardSector(Venue venue, String name, Integer capacity, SeatBlock seats) {

        static BillboardSector generalAdmission(Venue venue, String name, int capacity) {
            return new BillboardSector(venue, name, capacity, null);
        }

        static BillboardSector numbered(Venue venue, String name, SeatBlock seats) {
            return new BillboardSector(venue, name, null, seats);
        }

        SectorSale sale(Long eventSectorId, String price) {
            return new SectorSale(eventSectorId, name, new BigDecimal(price), capacity,
                    seats == null ? null : seats.seats());
        }
    }

    private record BillboardEvent(String name, BillboardSector sector) {
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
        seedBillboardSales();
        sellOutTheJazzRoom();
        sellOutTheFolkNightStalls();
        seedPaidFieldOrder();
        seedPaidSeatOrder();
        seedPastEventOrder();
        seedPendingSeatOrder();
        seedSaleShowcase();

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
        sale(FOLK_NIGHT_EVENT_ID, "Peña folklórica serrana", THEATER, EventSaleStatus.PUBLISHED, at(18, 21, 0), null,
                false, 8,
                numbered(FOLK_NIGHT_STALLS_SECTOR_ID, "Platea", "9000", THEATER_STALLS),
                numbered(16L, "Pullman", "7000", THEATER_BALCONY));
    }

    /**
     * Same order, names, sectors, dates and prices as the EventSeeder's billboard, so every published event the
     * front lists has its availability.
     */
    private void seedBillboardSales() {
        List<BillboardEvent> billboard = List.of(
                new BillboardEvent("Noche de tango", STADIUM_FIELD),
                new BillboardEvent("Rock del Litoral", LITORAL_TERRACE),
                new BillboardEvent("Cuarteto en la Arena", ARENA_FIELD),
                new BillboardEvent("Jazz de medianoche", HARBOR_ROOM),
                new BillboardEvent("Coro de cámara", THEATER_STALLS_SECTOR),
                new BillboardEvent("Orquesta sinfónica juvenil", THEATER_STALLS_SECTOR),
                new BillboardEvent("Pop nacional en vivo", STADIUM_FIELD),
                new BillboardEvent("Festival de cerveza artesanal", ARENA_FIELD),
                new BillboardEvent("Festival del Río", LITORAL_TERRACE),
                new BillboardEvent("Festival de jazz costero", HARBOR_ROOM),
                new BillboardEvent("Festival de rock del Sur", STADIUM_FIELD),
                new BillboardEvent("Encuentro de murgas", ARENA_FIELD),
                new BillboardEvent("Hamlet", THEATER_STALLS_SECTOR),
                new BillboardEvent("Bodas de sangre", THEATER_BALCONY_SECTOR),
                new BillboardEvent("La nona", THEATER_STALLS_SECTOR),
                new BillboardEvent("Comedia de enredos", HARBOR_ROOM),
                new BillboardEvent("El método Grönholm", THEATER_BALCONY_SECTOR),
                new BillboardEvent("Improvisación en vivo", HARBOR_ROOM),
                new BillboardEvent("Final de vóley", ARENA_FIELD),
                new BillboardEvent("Torneo de básquet", ARENA_FIELD),
                new BillboardEvent("Litoral vs. Paraná", LITORAL_TERRACE),
                new BillboardEvent("Copa de rugby del Sur", STADIUM_FIELD),
                new BillboardEvent("Maratón nocturna", STADIUM_FIELD),
                new BillboardEvent("Clásico de handball", LITORAL_TERRACE),
                new BillboardEvent("Congreso de desarrollo web", ARENA_FIELD),
                new BillboardEvent("Jornada de datos abiertos", HARBOR_ROOM),
                new BillboardEvent("Charla de diseño de producto", THEATER_STALLS_SECTOR),
                new BillboardEvent("Encuentro de emprendedores", LITORAL_TERRACE),
                new BillboardEvent("Seminario de ciberseguridad", ARENA_FIELD),
                new BillboardEvent("Foro de energías renovables", STADIUM_FIELD)
        );
        for (int index = 0; index < billboard.size(); index++) {
            BillboardEvent item = billboard.get(index);
            String price = String.valueOf(BILLBOARD_BASE_PRICE + index * BILLBOARD_PRICE_STEP);
            sale(BILLBOARD_FIRST_EVENT_ID + index, item.name(), item.sector().venue(), EventSaleStatus.PUBLISHED,
                    at(BILLBOARD_FIRST_DAY + index * BILLBOARD_DAYS_APART, 21, 0), null, false,
                    BILLBOARD_MAX_TICKETS, item.sector().sale(BILLBOARD_FIRST_EVENT_SECTOR_ID + index, price));
        }
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

    /**
     * Anchor for an event on sale whose first sector is sold out while the second one still has seats.
     */
    private void sellOutTheFolkNightStalls() {
        eventSeatRepository.findByEventIdOrderBySeatId(FOLK_NIGHT_EVENT_ID).stream()
                .filter(seat -> FOLK_NIGHT_STALLS_SECTOR_ID.equals(seat.getEventSectorId()))
                .forEach(seat -> seat.setStatus(EventSeatStatus.SOLD));
    }

    /**
     * The same events as the EventSeeder's showcase, each one a step between available and sold out. The seats and
     * places are taken without orders, like the folk night's stalls.
     */
    private void seedSaleShowcase() {
        showcaseSale(BIG_NIGHT_EVENT_ID, "Noche grande en la Ribera", 170, 48L);
        showcaseSale(DERBY_EVENT_ID, "Clásico platense", 175, 52L);
        showcaseSale(COAST_FESTIVAL_EVENT_ID, "Festival de la Costanera", 180, 56L);
        sale(CUP_FINAL_EVENT_ID, "Final de la Copa Ribera", RIBERA, EventSaleStatus.PUBLISHED, at(185, 21, 0), null,
                false, SHOWCASE_MAX_TICKETS,
                numbered(CUP_FINAL_LOWER_STAND_SECTOR_ID, "Platea baja", "68000", RIBERA_LOWER_STAND),
                numbered(CUP_FINAL_VIP_BOX_SECTOR_ID, "Palco VIP", "120000", RIBERA_VIP_BOX));

        sellSeats(BIG_NIGHT_EVENT_ID, BIG_NIGHT_LOWER_STAND_SECTOR_ID,
                seat -> seat.getSeatId() % BIG_NIGHT_SOLD_EVERY == 0);
        sellSeats(BIG_NIGHT_EVENT_ID, BIG_NIGHT_UPPER_STAND_SECTOR_ID, seat -> true);
        takePlaces(DERBY_FIELD_SECTOR_ID, RIBERA_FIELD_CAPACITY);
        takePlaces(COAST_FESTIVAL_FIELD_SECTOR_ID, RIBERA_FIELD_CAPACITY - 1);
        sellSeats(CUP_FINAL_EVENT_ID, CUP_FINAL_LOWER_STAND_SECTOR_ID,
                seat -> !CUP_FINAL_LAST_FREE_SEAT_ID.equals(seat.getSeatId()));
        sellSeats(CUP_FINAL_EVENT_ID, CUP_FINAL_VIP_BOX_SECTOR_ID, seat -> true);
    }

    /** Campo, Platea baja, Platea alta and Palco VIP, from {@code firstEventSectorId} on. */
    private void showcaseSale(Long eventId, String name, int daysFromToday, long firstEventSectorId) {
        sale(eventId, name, RIBERA, EventSaleStatus.PUBLISHED, at(daysFromToday, 21, 0), null, false,
                SHOWCASE_MAX_TICKETS,
                generalAdmission(firstEventSectorId, "Campo", "52000", RIBERA_FIELD_CAPACITY),
                numbered(firstEventSectorId + 1, "Platea baja", "68000", RIBERA_LOWER_STAND),
                numbered(firstEventSectorId + 2, "Platea alta", "85000", RIBERA_UPPER_STAND),
                numbered(firstEventSectorId + 3, "Palco VIP", "120000", RIBERA_VIP_BOX));
    }

    private void sellSeats(Long eventId, Long eventSectorId, Predicate<EventSeat> isSold) {
        eventSeatRepository.findByEventIdOrderBySeatId(eventId).stream()
                .filter(seat -> eventSectorId.equals(seat.getEventSectorId()))
                .filter(isSold)
                .forEach(seat -> seat.setStatus(EventSeatStatus.SOLD));
    }

    private void takePlaces(Long eventSectorId, int places) {
        eventSectorStockRepository.findById(eventSectorId).orElseThrow().setTaken(places);
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

    private void seedPastEventOrder() {
        EventSectorPrice room = sectorOf(FINISHED_TALK_SECTOR_ID);
        Order order = orderRepository.save(order(FINISHED_TALK_EVENT_ID, OrderStatus.PAID, room.getPrice())
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build());

        ticketRepository.save(issuedTicket(order, room).build());

        EventSectorStock stock = eventSectorStockRepository.findById(FINISHED_TALK_SECTOR_ID).orElseThrow();
        stock.setTaken(stock.getTaken() + 1);
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
