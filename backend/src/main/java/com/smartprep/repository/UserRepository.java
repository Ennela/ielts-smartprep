package com.smartprep.repository;

import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username, String email, Pageable pageable);

    long countByRole(Role role);

    /** The admin user list filtered to one role, with an optional username/email search. */
    @Query("SELECT u FROM User u WHERE u.role = :role AND (:search IS NULL"
            + " OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))"
            + " OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findByRoleAndSearch(@Param("role") Role role, @Param("search") String search, Pageable pageable);
}

