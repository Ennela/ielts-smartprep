package com.smartprep.service;

import com.smartprep.dto.response.AdminDashboardResponse;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The admin dashboard figures: none of them is a constant any more. */
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private MockTestSubmissionRepository mockTestSubmissionRepository;
    @Mock private HealthEndpoint healthEndpoint;

    @InjectMocks private AdminDashboardService service;

    @Test
    @DisplayName("counts students only, mock-test Writing still grading as pending, and reports the health check")
    void figuresComeFromTheirSources() {
        when(userRepository.countByRole(Role.STUDENT)).thenReturn(9L);
        when(scoreHistoryRepository.countByRecordedAtAfter(any())).thenReturn(4L);
        when(mockTestSubmissionRepository.countByStatus(SubmissionStatus.GRADING)).thenReturn(2L);
        doReturn(Health.up().build()).when(healthEndpoint).health();

        AdminDashboardResponse stats = service.getDashboardStats();

        assertEquals(9L, stats.getTotalUsers());
        assertEquals(4L, stats.getTestsToday());
        assertEquals(2L, stats.getPendingWritings());
        assertTrue(stats.isApiHealthy());
        verify(userRepository, never()).count();
    }

    @Test
    @DisplayName("a failing health check shows as unhealthy instead of a fixed 'Healthy'")
    void unhealthyWhenHealthCheckIsDown() {
        doReturn(Health.down().build()).when(healthEndpoint).health();

        assertFalse(service.getDashboardStats().isApiHealthy());
    }
}
