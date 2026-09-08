package com.smartprep.repository;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the property the AI services depend on: a method that is <em>not</em>
 * {@code @Transactional} does not keep a pooled database connection while it runs.
 *
 * <p>This is the whole basis for how the AI-in-transaction defect is fixed. Those services
 * read some rows, call Gemini for up to 65 seconds with three retries, then write a row. If
 * a connection were pinned for the lifetime of the enclosing method rather than for each
 * unit of database work, dropping {@code @Transactional} would achieve nothing and the pool
 * would still be exhausted by concurrent generation.
 *
 * <p>It is asserted rather than assumed because the answer is not obvious from the code:
 * Hibernate's connection handling mode decides it, {@code spring.jpa.open-in-view} keeps an
 * EntityManager open for the whole web request regardless, and neither is visible at the
 * call site. If a future Hibernate or Spring Boot upgrade changes the acquisition mode to
 * hold connections for the session, this test fails and says so — rather than the
 * application quietly returning to exhausting its pool under load.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
// @DataJpaTest wraps every test method in a transaction, which would hold a connection for
// the whole method and make the measurement below meaningless -- the "outside a transaction"
// case would never actually be outside one. Opting out is the entire point of this class.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConnectionHoldingIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private HikariPoolMXBean pool() {
        return ((HikariDataSource) dataSource).getHikariPoolMXBean();
    }

    @Test
    @DisplayName("a repository read outside a transaction releases its connection immediately")
    void readOutsideATransactionDoesNotPinAConnection() {
        // Warm the pool so the assertion is about holding, not about lazy creation.
        userRepository.count();

        int before = pool().getActiveConnections();

        userRepository.count();
        userRepository.count();

        // This is the moment that matters. It stands in for the Gemini call: control has
        // returned to a method with no transaction, and nothing should still be checked out.
        assertThat(pool().getActiveConnections())
                .as("a connection is still checked out after a non-transactional read, so an "
                        + "AI call made here would hold it for the whole round trip")
                .isEqualTo(before)
                .isZero();
    }

    @Test
    @DisplayName("a transaction does hold its connection for the whole block")
    void aTransactionPinsAConnectionUntilItCommits() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        // The control case. Without this the test above could pass simply because the pool
        // never reports anything as active, which would make it prove nothing at all.
        int insideTransaction = template.execute(status -> {
            userRepository.count();
            return pool().getActiveConnections();
        });

        assertThat(insideTransaction)
                .as("a transactional block must hold exactly the connection it is using")
                .isEqualTo(1);
        assertThat(pool().getActiveConnections())
                .as("and must give it back on commit")
                .isZero();
    }
}
