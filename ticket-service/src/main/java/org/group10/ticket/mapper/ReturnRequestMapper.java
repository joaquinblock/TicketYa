package org.group10.ticket.mapper;

import org.group10.ticket.dto.ReturnRequestPageResponse;
import org.group10.ticket.dto.ReturnRequestResponse;
import org.group10.ticket.entity.ReturnRequest;
import org.springframework.data.domain.Page;

public final class ReturnRequestMapper {

    private ReturnRequestMapper() {
    }

    public static ReturnRequestResponse toResponse(ReturnRequest request) {
        return new ReturnRequestResponse(request.getId(), request.getTicket().getId(),
                request.getTicket().getOrder().getEventId(), request.getRequestedBy(), request.getRequestedAt(),
                request.getStatus(), request.getResolvedBy(), request.getResolvedAt());
    }

    public static ReturnRequestPageResponse toPageResponse(Page<ReturnRequest> page) {
        return new ReturnRequestPageResponse(page.map(ReturnRequestMapper::toResponse).getContent(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
