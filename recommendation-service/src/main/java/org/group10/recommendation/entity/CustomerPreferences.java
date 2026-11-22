package org.group10.recommendation.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Lo que el cliente elige que le gusta (US-REC-01). Un documento por cliente: se lee y se reemplaza entero.
 * {@code userId} es el id del auth-service y {@code participantIds} son ids del event-service (RFC-004).
 */
@Document(collection = "preferences")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomerPreferences {

    @Id
    private Long userId;

    private List<EventType> eventTypes;

    private List<Long> participantIds;

    private String city;
}
