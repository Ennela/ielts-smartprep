package com.smartprep.repository;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Shared Testcontainers MySQL base class.
 * Reuses a single container across all integration test subclasses to speed up the suite.
 */
@Tag("integration")
public abstract class AbstractMySQLContainerTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ielts_smartprep_test")
            .withUsername("test")
            .withPassword("test");

    static {
        requireDocker();
        MYSQL.start();
    }

    /**
     * Fails the suite up front, with one message that names the cause, when no Docker
     * daemon is reachable.
     *
     * <p>Without this, {@code MYSQL.start()} throws deep inside Testcontainers and every
     * class in the profile reports {@code ExceptionInInitializerError} or "Could not find
     * a valid Docker environment" -- 43 stack traces that all mean "Docker Desktop is not
     * running". This is a fail, not an {@code Assumptions.abort()}: aborting would skip
     * the whole profile and let a CI run whose Docker was broken finish green, and these
     * are the only tests that check the entities against the Flyway schema.
     */
    protected static void requireDocker() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            // ASCII only: this is read on a Windows console, which garbles anything else.
            String message = "Docker daemon is not running -- start Docker Desktop and run the "
                    + "integration-tests profile again.";
            System.err.println(System.lineSeparator() + "[integration-tests] " + message + System.lineSeparator());
            throw new IllegalStateException(message);
        }
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        // validate, not none: this asserts every entity still matches the Flyway schema.
        // With "none" the suite stayed green while the two drifted apart across many
        // migrations — 16 columns had become native MySQL ENUM while others stayed VARCHAR
        // — and nothing caught it until the prod profile, which does validate, refused to
        // start. Set here rather than in application-test.yml because @DynamicPropertySource
        // takes precedence over the yml files.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
