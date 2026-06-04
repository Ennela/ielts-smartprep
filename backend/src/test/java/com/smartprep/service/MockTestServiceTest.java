package com.smartprep.service;

import com.smartprep.dto.request.MockTestProgressRequest;
import com.smartprep.dto.response.MockTestSessionResponse;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.*;
import com.smartprep.service.util.IeltsScoringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MockTestServiceTest {

    @Mock
    private MockTestRepository mockTestRepository;

    @Mock
    private MockTestSessionRepository sessionRepository;

    @Mock
    private MockTestSubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MockTestService mockTestService;

    private User testUser;
    private MockTest mockTest;
    private MockTestSection listeningSection;
    private MockTestSection readingSection;
    private MockTestSection writingSection;

    @BeforeEach
    public void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("student@test.com")
                .build();

        mockTest = MockTest.builder()
                .mockTestId(1L)
                .title("Cambridge IELTS 18 Test 1")
                .difficulty(Difficulty.PASSAGE_2)
                .build();

        listeningSection = MockTestSection.builder()
                .mockTest(mockTest)
                .sectionType(SkillType.LISTENING)
                .durationSeconds(2400)
                .sectionOrder(1)
                .build();

        readingSection = MockTestSection.builder()
                .mockTest(mockTest)
                .sectionType(SkillType.READING)
                .durationSeconds(3600)
                .sectionOrder(2)
                .build();

        writingSection = MockTestSection.builder()
                .mockTest(mockTest)
                .sectionType(SkillType.WRITING)
                .durationSeconds(3600)
                .sectionOrder(3)
                .build();

        mockTest.setSections(List.of(listeningSection, readingSection, writingSection));
    }

    @Test
    public void testStartOrResumeSession_NewSessionCreated() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(sessionRepository.findFirstByUserUserIdAndStatusOrderByStartedAtDesc(1L, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(mockTestRepository.findById(1L)).thenReturn(Optional.of(mockTest));
        
        MockTestSession savedSession = MockTestSession.builder()
                .sessionId(100L)
                .user(testUser)
                .mockTest(mockTest)
                .status(SessionStatus.IN_PROGRESS)
                .currentSection(SkillType.LISTENING)
                .timeRemainingSeconds(2400)
                .progressJson("{}")
                .build();

        when(sessionRepository.save(any(MockTestSession.class))).thenReturn(savedSession);

        MockTestSessionResponse response = mockTestService.startOrResumeSession(1L, 1L);

        assertNotNull(response);
        assertEquals(100L, response.getSessionId());
        assertEquals(SkillType.LISTENING, response.getCurrentSection());
        assertEquals(2400, response.getTimeRemainingSeconds());
        verify(sessionRepository, times(1)).save(any(MockTestSession.class));
    }

    @Test
    public void testNextSection_TransitionListeningToReading() {
        MockTestSession session = MockTestSession.builder()
                .sessionId(100L)
                .user(testUser)
                .mockTest(mockTest)
                .status(SessionStatus.IN_PROGRESS)
                .currentSection(SkillType.LISTENING)
                .timeRemainingSeconds(10)
                .progressJson("{}")
                .build();

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setCurrentSection(SkillType.LISTENING);
        request.setTimeRemainingSeconds(0);
        request.setProgressJson("{\"q1\":\"A\"}");

        MockTestSessionResponse response = mockTestService.nextSection(1L, 100L, request);

        assertNotNull(response);
        assertEquals(SkillType.READING, response.getCurrentSection());
        assertEquals(3600, response.getTimeRemainingSeconds()); // fetched from readingSection duration
    }

    @Test
    public void testNextSection_TransitionReadingToWriting() {
        MockTestSession session = MockTestSession.builder()
                .sessionId(100L)
                .user(testUser)
                .mockTest(mockTest)
                .status(SessionStatus.IN_PROGRESS)
                .currentSection(SkillType.READING)
                .timeRemainingSeconds(10)
                .progressJson("{}")
                .build();

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setCurrentSection(SkillType.READING);
        request.setTimeRemainingSeconds(0);
        request.setProgressJson("{\"q1\":\"A\",\"q41\":\"TRUE\"}");

        MockTestSessionResponse response = mockTestService.nextSection(1L, 100L, request);

        assertNotNull(response);
        assertEquals(SkillType.WRITING, response.getCurrentSection());
        assertEquals(3600, response.getTimeRemainingSeconds()); // fetched from writingSection duration
    }

    @Test
    public void testIeltsScoringRoundingRules() {
        // Rounding down case: fractional part < 0.25 (e.g. 6.125 -> 6.0)
        assertEquals(new BigDecimal("6.0"), IeltsScoringUtils.roundOverallBand(new BigDecimal("6.125")));
        assertEquals(new BigDecimal("6.0"), IeltsScoringUtils.roundOverallBand(new BigDecimal("6.20")));

        // Rounding to .5 case: 0.25 <= fractional part < 0.75 (e.g. 6.25 -> 6.5, 6.625 -> 6.5)
        assertEquals(new BigDecimal("6.5"), IeltsScoringUtils.roundOverallBand(new BigDecimal("6.25")));
        assertEquals(new BigDecimal("6.5"), IeltsScoringUtils.roundOverallBand(new BigDecimal("6.49")));
        assertEquals(new BigDecimal("6.5"), IeltsScoringUtils.roundOverallBand(new BigDecimal("6.625")));
        assertEquals(new BigDecimal("6.5"), IeltsScoringUtils.roundOverallBand(new BigDecimal("6.74")));

        // Rounding up case: fractional part >= 0.75 (e.g. 6.75 -> 7.0, 6.875 -> 7.0)
        assertEquals(new BigDecimal("7.0"), IeltsScoringUtils.roundOverallBand(new BigDecimal("6.75")));
        assertEquals(new BigDecimal("7.0"), IeltsScoringUtils.roundOverallBand(new BigDecimal("6.90")));
    }
}
