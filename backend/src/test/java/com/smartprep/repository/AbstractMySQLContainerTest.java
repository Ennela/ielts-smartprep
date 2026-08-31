package com.smartprep.repository;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        // TODO(B-65): switch to "validate" once the enum JDBC-type mapping is settled.
        //
        // This should be "validate" so the suite asserts entities still match the Flyway
        // schema — with "none" it stayed green while the two drifted apart for many
        // migrations, and only the prod profile (which does validate) caught it.
        //
        // Flipping it now turns the whole integration suite red for a reason unrelated to
        // the tests: Hibernate 6 maps @Enumerated(STRING) to a native MySQL ENUM, while
        // every migration creates VARCHAR. V45 normalised the columns, but setting
        // hibernate.type.preferred_enum_jdbc_type=VARCHAR in application.yml and
        // application-test.yml did not take effect — validation still expects ENUM.
        // Leaving a red suite behind would cost more than it documents.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}
