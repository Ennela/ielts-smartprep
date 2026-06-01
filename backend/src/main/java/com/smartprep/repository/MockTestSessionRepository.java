package com.smartprep.repository;

import com.smartprep.model.entity.MockTestSession;
import com.smartprep.model.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MockTestSessionRepository extends JpaRepository<MockTestSession, Long> {
    Optional<MockTestSession> findByUserUserIdAndStatus(Long userId, SessionStatus status);
    Optional<MockTestSession> findFirstByUserUserIdAndStatusOrderByStartedAtDesc(Long userId, SessionStatus status);
}
