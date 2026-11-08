package org.group10.event.mapper;

import org.group10.event.dto.SectorResponse;
import org.group10.event.dto.VenueDetailResponse;
import org.group10.event.dto.VenueRequest;
import org.group10.event.dto.VenueSummaryResponse;
import org.group10.event.entity.Venue;

import java.util.List;

public final class VenueMapper {

    private VenueMapper() {
    }

    public static Venue toEntity(VenueRequest request) {
        return Venue.builder()
                .name(request.name().trim())
                .address(request.address().trim())
                .city(request.city().trim())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
    }

    public static void update(Venue venue, VenueRequest request) {
        venue.setName(request.name().trim());
        venue.setAddress(request.address().trim());
        venue.setCity(request.city().trim());
        venue.setLatitude(request.latitude());
        venue.setLongitude(request.longitude());
    }

    public static VenueSummaryResponse toSummary(Venue venue) {
        return new VenueSummaryResponse(venue.getId(), venue.getName(), venue.getAddress(), venue.getCity(),
                venue.getLatitude(), venue.getLongitude());
    }

    public static VenueDetailResponse toDetail(Venue venue, List<SectorResponse> sectors) {
        int capacity = sectors.stream().mapToInt(SectorResponse::capacity).sum();
        return new VenueDetailResponse(venue.getId(), venue.getName(), venue.getAddress(), venue.getCity(),
                venue.getLatitude(), venue.getLongitude(), capacity, sectors);
    }
}
