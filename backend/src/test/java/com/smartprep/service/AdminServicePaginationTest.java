package com.smartprep.service;

import com.smartprep.dto.response.AdminUserResponse;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServicePaginationTest {

    @Mock private UserRepository userRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private WritingPromptRepository writingPromptRepository;
    @Mock private ReadingQuizRepository readingQuizRepository;
    @Mock private MockTestRepository mockTestRepository;
    @Mock private ListeningPartRepository listeningPartRepository;

    @InjectMocks private AdminService adminService;

    private List<User> sampleUsers;

    @BeforeEach
    void setUp() {
        sampleUsers = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            sampleUsers.add(User.builder()
                    .userId((long) i)
                    .username("user" + i)
                    .email("user" + i + "@test.com")
                    .displayName("User " + i)
                    .role(Role.STUDENT)
                    .targetReadingScore(new BigDecimal("6.5"))
                    .targetWritingScore(new BigDecimal("6.5"))
                    .targetListeningScore(new BigDecimal("6.5"))
                    .createdAt(LocalDateTime.now().minusDays(i))
                    .build());
        }
    }

    @Nested
    @DisplayName("listUsers pagination")
    class ListUsersPaginationTests {

        @Test
        @DisplayName("should return correct page content and metadata")
        void listUsers_returnsCorrectPage() {
            // First page of 10
            List<User> pageContent = sampleUsers.subList(0, 10);
            Page<User> userPage = new PageImpl<>(pageContent, PageRequest.of(0, 10), 25);

            when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);
            when(scoreHistoryRepository.getSkillAverages(anyLong())).thenReturn(Collections.emptyList());

            Page<AdminUserResponse> result = adminService.listUsers(null, 0, 10, "createdAt,desc");

            assertNotNull(result);
            assertEquals(10, result.getContent().size());
            assertEquals(25, result.getTotalElements());
            assertEquals(3, result.getTotalPages());
            assertEquals(0, result.getNumber());
            assertTrue(result.hasNext());
            assertFalse(result.isLast());

            verify(userRepository).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("should return second page correctly")
        void listUsers_secondPage() {
            List<User> pageContent = sampleUsers.subList(10, 20);
            Page<User> userPage = new PageImpl<>(pageContent, PageRequest.of(1, 10), 25);

            when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);
            when(scoreHistoryRepository.getSkillAverages(anyLong())).thenReturn(Collections.emptyList());

            Page<AdminUserResponse> result = adminService.listUsers(null, 1, 10, "createdAt,desc");

            assertEquals(10, result.getContent().size());
            assertEquals(1, result.getNumber());
            assertTrue(result.hasNext());
            assertFalse(result.isLast());
        }

        @Test
        @DisplayName("should work with search filter and pagination")
        void listUsers_withSearch() {
            User matchingUser = sampleUsers.get(0);
            Page<User> userPage = new PageImpl<>(List.of(matchingUser), PageRequest.of(0, 10), 1);

            when(userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    eq("user1"), eq("user1"), any(Pageable.class))).thenReturn(userPage);
            when(scoreHistoryRepository.getSkillAverages(anyLong())).thenReturn(Collections.emptyList());

            Page<AdminUserResponse> result = adminService.listUsers("user1", 0, 10, "createdAt,desc");

            assertEquals(1, result.getContent().size());
            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getTotalPages());
            assertEquals("user1", result.getContent().get(0).getUsername());
        }

        @Test
        @DisplayName("should clamp size to MAX_PAGE_SIZE (100)")
        void listUsers_clampSize() {
            Page<User> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 100), 0);

            when(userRepository.findAll(argThat((Pageable p) -> p.getPageSize() <= 100)))
                    .thenReturn(emptyPage);

            Page<AdminUserResponse> result = adminService.listUsers(null, 0, 500, "createdAt,desc");

            assertNotNull(result);
            // Verify that the repository was called with size <= 100
            verify(userRepository).findAll(argThat((Pageable p) -> p.getPageSize() <= 100));
        }

        @Test
        @DisplayName("should return empty page when no results")
        void listUsers_emptyResult() {
            Page<User> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

            when(userRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

            Page<AdminUserResponse> result = adminService.listUsers(null, 0, 10, "createdAt,desc");

            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());
            assertEquals(0, result.getTotalElements());
            assertEquals(0, result.getTotalPages());
        }

        @Test
        @DisplayName("should handle ascending sort")
        void listUsers_ascendingSort() {
            Page<User> userPage = new PageImpl<>(sampleUsers.subList(0, 5), PageRequest.of(0, 5), 25);

            when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);
            when(scoreHistoryRepository.getSkillAverages(anyLong())).thenReturn(Collections.emptyList());

            Page<AdminUserResponse> result = adminService.listUsers(null, 0, 5, "createdAt,asc");

            assertNotNull(result);
            assertEquals(5, result.getContent().size());
            // Verify sort direction was passed correctly
            verify(userRepository).findAll(argThat((Pageable p) ->
                    p.getSort().getOrderFor("createdAt") != null &&
                    p.getSort().getOrderFor("createdAt").getDirection().isAscending()));
        }

        @Test
        @DisplayName("should use default sort when sort string is null")
        void listUsers_defaultSort() {
            Page<User> userPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

            when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);

            adminService.listUsers(null, 0, 10, null);

            verify(userRepository).findAll(argThat((Pageable p) ->
                    p.getSort().getOrderFor("createdAt") != null &&
                    p.getSort().getOrderFor("createdAt").getDirection().isDescending()));
        }
    }
}
