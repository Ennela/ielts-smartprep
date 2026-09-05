package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.TestMode;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the IDOR on {@code GET /api/v1/listening/{testId}/result}.
 * <p>
 * The endpoint previously took no authenticated principal at all and the service
 * looked the test up with a bare {@code findById(testId)}, so any authenticated user
 * could read any other user's answers, band score and transcript simply by
 * incrementing the auto-increment {@code testId}.
 * <p>
 * These tests pin the fix in place: the lookup must be scoped by owner, and a test
 * belonging to someone else must be indistinguishable from one that does not exist.
 */
@ExtendWith(MockitoExtension.class)
class ListeningQueryServiceOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long TEST_ID = 42L;

    @Mock private ListeningPartRepository partRepository;
    @Mock private ListeningTestRepository testRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ListeningQueryService listeningQueryService;

    @Test
    @DisplayName("returns the result when the caller owns the listening test")
    void getTestResult_ownerCanRead() {
        User owner = User.builder().userId(OWNER_ID).build();
        ListeningTest test = ListeningTest.builder()
                .testId(TEST_ID)
                .user(owner)
                .testMode(TestMode.PRACTICE)
                .testParts(new ArrayList<>())
                .build();

        when(testRepository.findByTestIdAndUserUserId(TEST_ID, OWNER_ID))
                .thenReturn(Optional.of(test));

        assertNotNull(listeningQueryService.getTestResult(TEST_ID, OWNER_ID));
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when the test belongs to another user")
    void getTestResult_otherUsersTestIsNotFound() {
        // The owner-scoped query returns empty for a test the attacker does not own,
        // so the attacker gets a 404 rather than the owner's answers.
        when(testRepository.findByTestIdAndUserUserId(TEST_ID, ATTACKER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> listeningQueryService.getTestResult(TEST_ID, ATTACKER_ID));
    }

    @Test
    @DisplayName("never falls back to an unscoped findById lookup")
    void getTestResult_doesNotUseUnscopedLookup() {
        when(testRepository.findByTestIdAndUserUserId(TEST_ID, ATTACKER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> listeningQueryService.getTestResult(TEST_ID, ATTACKER_ID));

        // Guards against a future refactor reintroducing the vulnerable lookup.
        verify(testRepository, never()).findById(eq(TEST_ID));
    }
}
