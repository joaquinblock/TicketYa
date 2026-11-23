package org.group10.event.config;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The caches of the catalog and what each change evicts (ADR 0009). Only the public view is cached: a draft never
 * is, and neither is anything that depends on who asks. Availability lives in the ticket-service and isn't cached.
 */
public final class CatalogCaches {

    /** Detail of an event that isn't a draft, by id. */
    public static final String EVENT = "event";
    /** First page of the search with no filters: what any visitor sees first. */
    public static final String EVENTS_UPCOMING = "events-upcoming";
    /** Venue with its sectors, by id. */
    public static final String VENUE = "venue";
    /** Participant, by id. */
    public static final String PARTICIPANT = "participant";

    /** The only key of {@link #EVENTS_UPCOMING}. */
    public static final String FIRST_PAGE = "first-page";

    private CatalogCaches() {
    }

    /**
     * The status or the data of the event {@code id} changed: its detail and the initial list, where it may appear.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Caching(evict = {
            @CacheEvict(cacheNames = EVENT, key = "#id"),
            @CacheEvict(cacheNames = EVENTS_UPCOMING, allEntries = true)})
    public @interface EvictsEvent {
    }

    /**
     * A venue, a sector or a participant changed. They're copied into the detail of every event that has them, and
     * finding those events isn't worth it for changes this rare: the event caches are emptied.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Caching(evict = {
            @CacheEvict(cacheNames = EVENT, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_UPCOMING, allEntries = true)})
    public @interface EvictsAllEvents {
    }
}
