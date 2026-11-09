package org.group10.auth.repository;

import org.group10.auth.entity.Organizer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizerRepository extends JpaRepository<Organizer, Long> {

    /**
     * Same as {@link #findById} but also loads the user and the organization, which are lazy.
     */
    @EntityGraph(attributePaths = {"user", "organization"})
    Optional<Organizer> findWithUserAndOrganizationByUserId(Long userId);
}
