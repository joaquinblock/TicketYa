package org.group10.auth.repository;

import org.group10.auth.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Same as {@link #findById} but also loads the role and its permissions, which are lazy.
     */
    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findWithRoleAndPermissionsById(Long id);
}
