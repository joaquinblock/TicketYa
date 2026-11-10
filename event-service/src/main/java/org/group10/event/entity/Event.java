package org.group10.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.group10.event.statemachine.EventStatusMachine;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Column(nullable = false)
    private Long organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    /** No setter: it only changes through {@link #changeStatus}, so nobody can skip the state machine. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private EventStatus status;

    /**
     * {@code PUBLISHED} or {@code SOLD_OUT} while the event is suspended, so resuming goes back to it; {@code null}
     * otherwise. Without it, a sold out event would reopen the sale as if it had room.
     */
    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private EventStatus statusBeforeSuspension;

    @Column(nullable = false)
    private boolean highDemand;

    private LocalDateTime saleStart;

    private Integer maxTicketsPerCustomer;

    @Column(nullable = false)
    private Long createdBy;

    @ManyToMany
    @JoinTable(
            name = "event_participant",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "participant_id")
    )
    @Builder.Default
    private Set<Participant> participants = new HashSet<>();

    /**
     * Changes the status through the state machine.
     *
     * @throws org.group10.common.exception.TicketYaException {@code INVALID_STATUS_TRANSITION} if not allowed
     */
    public void changeStatus(EventStatus next, EventStatusMachine machine) {
        this.status = machine.transition(status, next);
        if (status != EventStatus.SUSPENDED) {
            statusBeforeSuspension = null;
        }
    }

    /**
     * Suspends the event and remembers the status it had. Suspending one that is already suspended changes nothing.
     */
    public void suspend(EventStatusMachine machine) {
        EventStatus previous = status;
        changeStatus(EventStatus.SUSPENDED, machine);
        if (previous != EventStatus.SUSPENDED) {
            statusBeforeSuspension = previous;
        }
    }

    /**
     * Goes back to the status it had before being suspended. An event loaded as suspended with no previous status
     * (e.g. by the seeder) goes back to {@code PUBLISHED}.
     */
    public void resume(EventStatusMachine machine) {
        changeStatus(statusToResumeTo(), machine);
    }

    public void changeAvailabilityTo(EventStatus availability, EventStatusMachine machine) {
        if (status == EventStatus.SUSPENDED) {
            statusBeforeSuspension = availability;
        } else {
            changeStatus(availability, machine);
        }
    }

    public EventStatus statusToResumeTo() {
        return statusBeforeSuspension == null ? EventStatus.PUBLISHED : statusBeforeSuspension;
    }
}
