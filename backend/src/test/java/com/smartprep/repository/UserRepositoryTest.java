package com.smartprep.repository;

import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link UserRepository}.
 * Uses shared Testcontainers MySQL container.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest extends AbstractMySQLContainerTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("should save a user and find it by username")
    void saveAndFindByUsername() {
        User user = User.builder()
                .username("integration_test_user")
                .passwordHash("secure_password")
                .email("integration@example.com")
                .displayName("Integration Test")
                .role(Role.STUDENT)
                .build();

        User savedUser = userRepository.save(user);
        assertThat(savedUser.getUserId()).isNotNull();

        Optional<User> foundUser = userRepository.findByUsername("integration_test_user");
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("integration@example.com");
    }

    @Test
    @DisplayName("should check if user exists by username and email")
    void existsByUsernameAndEmail() {
        User user = User.builder()
                .username("exist_check_user")
                .passwordHash("password")
                .email("exists@example.com")
                .role(Role.STUDENT)
                .build();

        userRepository.save(user);

        assertThat(userRepository.existsByUsername("exist_check_user")).isTrue();
        assertThat(userRepository.existsByUsername("non_existent")).isFalse();

        assertThat(userRepository.existsByEmail("exists@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("other@example.com")).isFalse();
    }
}
