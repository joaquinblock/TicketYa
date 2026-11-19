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

    /**
     * The user that already logged in with this Google account ({@code sub} of the Google ID token).
     */
    Optional<User> findByIdpSubject(String idpSubject);

    /**
     * Google lower-cases the address it verifies, but a preloaded user may have been written with capitals.
     */
    Optional<User> findByEmailIgnoreCase(String email);
}
