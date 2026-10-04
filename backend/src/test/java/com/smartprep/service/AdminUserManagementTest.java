package com.smartprep.service;

import com.smartprep.dto.request.AdminUserUpdateRequest;
import com.smartprep.dto.response.AdminUserResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Admin account management: role changes, suspension, the role filter on the user list
 * and the student-only dashboard count. An admin must never be able to lock themselves out.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserManagementTest {

    @Mock private UserRepository userRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private WritingPromptRepository writingPromptRepository;
    @Mock private ReadingQuizRepository readingQuizRepository;
    @Mock private MockTestRepository mockTestRepository;
    @Mock private ListeningPartRepository listeningPartRepository;

    @InjectMocks private AdminService adminService;

    private User student;

    @BeforeEach
    void setUp() {
        student = User.builder()
                .userId(7L).username("learner").email("learner@test.com")
                .role(Role.STUDENT).createdAt(LocalDateTime.now())
                .build();
    }

    private AdminUserUpdateRequest request(String role, Boolean suspended) {
        AdminUserUpdateRequest r = new AdminUserUpdateRequest();
        r.setRole(role);
        r.setSuspended(suspended);
        return r;
    }

    @Test
    @DisplayName("promotes a student to admin")
    void changesRole() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(userRepository.save(student)).thenReturn(student);
        when(scoreHistoryRepository.getSkillAverages(7L)).thenReturn(List.of());

        AdminUserResponse result = adminService.updateUser(1L, 7L, request("admin", null));

        assertEquals(Role.ADMIN, student.getRole());
        assertEquals("ADMIN", result.getRole());
    }

    @Test
    @DisplayName("suspends and reactivates an account without touching its role")
    void suspendsAndReactivates() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(userRepository.save(student)).thenReturn(student);
        when(scoreHistoryRepository.getSkillAverages(7L)).thenReturn(List.of());

        assertTrue(adminService.updateUser(1L, 7L, request(null, true)).isSuspended());
        assertEquals(Role.STUDENT, student.getRole());

        assertFalse(adminService.updateUser(1L, 7L, request(null, false)).isSuspended());
    }

    @Test
    @DisplayName("refuses to change the acting admin's own account")
    void refusesSelfChange() {
        assertThrows(IllegalArgumentException.class, () -> adminService.updateUser(7L, 7L, request("STUDENT", null)));
        assertThrows(IllegalArgumentException.class, () -> adminService.updateUser(7L, 7L, request(null, true)));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects an unknown role and a missing user")
    void rejectsBadInput() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        assertThrows(IllegalArgumentException.class, () -> adminService.updateUser(1L, 7L, request("TEACHER", null)));

        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adminService.updateUser(1L, 99L, request(null, true)));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("filters the user list by role, with the search term")
    void listsByRole() {
        Page<User> page = new PageImpl<>(List.of(student));
        when(userRepository.findByRoleAndSearch(eq(Role.STUDENT), eq("learn"), any(Pageable.class))).thenReturn(page);
        when(scoreHistoryRepository.getSkillAverages(7L)).thenReturn(List.of());

        Page<AdminUserResponse> result = adminService.listUsers("learn", "student", 0, 20, "createdAt,desc");

        assertEquals(1, result.getTotalElements());
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("counts only students as 'Total Students' on the dashboard")
    void dashboardCountsStudents() {
        when(userRepository.countByRole(Role.STUDENT)).thenReturn(9L);

        assertEquals(9L, adminService.getDashboardStats().getTotalUsers());
        verify(userRepository, never()).count();
    }
}
