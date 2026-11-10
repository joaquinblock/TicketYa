package org.group10.ticket.dto;

import java.util.List;

public record ReturnRequestPageResponse(
        List<ReturnRequestResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
