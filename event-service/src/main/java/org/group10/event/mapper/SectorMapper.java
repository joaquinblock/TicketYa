package org.group10.event.mapper;

import org.group10.event.dto.SeatResponse;
import org.group10.event.dto.SectorResponse;
import org.group10.event.entity.Seat;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;

public final class SectorMapper {

    private SectorMapper() {
    }

    public static Sector toEntity(Venue venue, String name, boolean numbered, Integer capacity) {
        return Sector.builder()
                .venue(venue)
                .name(name)
                .numbered(numbered)
                .capacity(capacity)
                .build();
    }

    /**
     * @param seatCount seats of the sector; ignored for general admission sectors, whose capacity is stored
     */
    public static SectorResponse toResponse(Sector sector, long seatCount) {
        int capacity = sector.isNumbered() ? Math.toIntExact(seatCount) : sector.getCapacity();
        return new SectorResponse(sector.getId(), sector.getName(), sector.isNumbered(), capacity);
    }

    public static SeatResponse toResponse(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getRow(), seat.getNumber());
    }
}
