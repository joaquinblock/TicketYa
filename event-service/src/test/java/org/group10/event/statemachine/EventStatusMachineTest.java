package org.group10.event.statemachine;

import org.group10.common.exception.TicketYaException;
import org.group10.event.entity.EventStatus;
import org.group10.event.exception.EventErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.group10.event.entity.EventStatus.CANCELLED;
import static org.group10.event.entity.EventStatus.DRAFT;
import static org.group10.event.entity.EventStatus.FINISHED;
import static org.group10.event.entity.EventStatus.PUBLISHED;
import static org.group10.event.entity.EventStatus.SOLD_OUT;
import static org.group10.event.entity.EventStatus.SUSPENDED;

/**
 * The expected table is the one in {@code event-state-machine.md}: if one changes, so does the other.
 */
class EventStatusMachineTest {

    private static final Map<EventStatus, Set<EventStatus>> EXPECTED = new EnumMap<>(Map.of(
            DRAFT, EnumSet.of(PUBLISHED),
            PUBLISHED, EnumSet.of(SOLD_OUT, SUSPENDED, CANCELLED, FINISHED),
            SOLD_OUT, EnumSet.of(PUBLISHED, SUSPENDED, CANCELLED, FINISHED),
            SUSPENDED, EnumSet.of(PUBLISHED, SOLD_OUT, CANCELLED),
            CANCELLED, EnumSet.noneOf(EventStatus.class),
            FINISHED, EnumSet.noneOf(EventStatus.class)));

    private final EventStatusMachine machine = new EventStatusMachine();

    @Test
    void everyStatusIsInTheTable_soANewOneCantBeForgotten() {
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(EventStatus.values());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairsOfDifferentStatuses")
    void transition_followsTheTable(EventStatus current, EventStatus next) {
        if (EXPECTED.get(current).contains(next)) {
            assertThat(machine.transition(current, next)).isEqualTo(next);
        } else {
            assertThatThrownBy(() -> machine.transition(current, next))
                    .isInstanceOf(TicketYaException.class)
                    .extracting(e -> ((TicketYaException) e).getErrorCode())
                    .isEqualTo(EventErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @ParameterizedTest
    @EnumSource(EventStatus.class)
    void transition_toTheSameStatus_doesNothing(EventStatus status) {
        assertThat(machine.transition(status, status)).isEqualTo(status);
    }

    @Test
    void transition_withNull_isABug() {
        assertThatThrownBy(() -> machine.transition(null, PUBLISHED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> machine.transition(DRAFT, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transition_rejected_saysFromWhereToWhere() {
        assertThatThrownBy(() -> machine.transition(FINISHED, PUBLISHED))
                .hasMessage("Un evento en FINISHED no puede pasar a PUBLISHED");
    }

    static Stream<Arguments> allPairsOfDifferentStatuses() {
        return Arrays.stream(EventStatus.values()).flatMap(current -> Arrays.stream(EventStatus.values())
                .filter(next -> next != current)
                .map(next -> Arguments.of(current, next)));
    }
}
