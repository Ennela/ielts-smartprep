package com.smartprep.service;

import com.smartprep.dto.response.AdminDashboardResponse;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** The figures on the admin dashboard. */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final MockTestSubmissionRepository mockTestSubmissionRepository;
    private final HealthEndpoint healthEndpoint;

    public AdminDashboardResponse getDashboardStats() {
        // "Total Students" on the dashboard: admin accounts are not learners.
        long totalUsers = userRepository.countByRole(Role.STUDENT);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long testsToday = scoreHistoryRepository.countByRecordedAtAfter(todayStart);
        // A Writing practice essay is graded before it is saved, so it is never pending. A mock
        // test's Writing is graded afterwards, and the submission waits as GRADING until then
        // (this used to be a hard-coded 0).
        long pendingWritings = mockTestSubmissionRepository.countByStatus(SubmissionStatus.GRADING);
        // The same aggregate /actuator/health reports (database, Redis, disk), not a constant.
        boolean apiHealthy = Status.UP.equals(healthEndpoint.health().getStatus());
        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .testsToday(testsToday)
                .pendingWritings(pendingWritings)
                .apiHealthy(apiHealthy)
                .build();
    }
}
