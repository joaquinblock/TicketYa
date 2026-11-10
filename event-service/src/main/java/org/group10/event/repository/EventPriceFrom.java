package org.group10.event.repository;

import java.math.BigDecimal;

/**
 * The cheapest price an event sells, for the search results.
 */
public record EventPriceFrom(Long eventId, BigDecimal price) {}
