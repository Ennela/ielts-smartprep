package com.smartprep.repository;

import com.smartprep.model.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ielts_smartprep_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

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
                .role(com.smartprep.model.enums.Role.STUDENT)
                .build();

        User savedUser = userRepository.save(user);
        assertNotNull(savedUser.getUserId());

        Optional<User> foundUser = userRepository.findByUsername("integration_test_user");
        assertTrue(foundUser.isPresent());
        assertEquals("integration@example.com", foundUser.get().getEmail());
    }

    @Test
    @DisplayName("should check if user exists by username and email")
    void existsByUsernameAndEmail() {
        User user = User.builder()
                .username("exist_check_user")
                .passwordHash("password")
                .email("exists@example.com")
                .role(com.smartprep.model.enums.Role.STUDENT)
                .build();

        userRepository.save(user);

        assertTrue(userRepository.existsByUsername("exist_check_user"));
        assertFalse(userRepository.existsByUsername("non_existent"));

        assertTrue(userRepository.existsByEmail("exists@example.com"));
        assertFalse(userRepository.existsByEmail("other@example.com"));
    }
}
