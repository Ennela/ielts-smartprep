package com.smartprep.service;

import com.smartprep.dto.request.AdminListeningPartRequest;
import com.smartprep.dto.response.AdminListeningPartResponse;
import com.smartprep.dto.response.AdminListeningStatsResponse;
import com.smartprep.dto.response.QuestionOptionResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.QuestionOption;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.repository.ListeningPartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminListeningService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ListeningPartRepository partRepository;
    private final AudioGenerationService audioGenerationService;
    private final StorageService storageService;

    /**
     * List all listening parts with pagination and filtering by audioStatus & topic.
     */
    @Transactional(readOnly = true)
    public Page<AdminListeningPartResponse> listParts(String audioStatusStr, String topic, int page, int size, String sort) {
        size = Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(page, size, parseSort(sort, "createdAt"));
        AudioStatus audioStatus = null;
        if (audioStatusStr != null && !audioStatusStr.isBlank()) {
            audioStatus = AudioStatus.valueOf(audioStatusStr.toUpperCase());
        }

        String cleanTopic = (topic != null && !topic.isBlank()) ? topic : null;

        Page<ListeningPart> partPage = partRepository.findByFilters(audioStatus, cleanTopic, pageRequest);
        return partPage.map(this::toAdminPartResponse);
    }

    /**
     * Get a detailed listening part (including correct answers) by ID.
     */
    @Transactional(readOnly = true)
    public AdminListeningPartResponse getPartById(Long partId) {
        ListeningPart part = partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening part not found: " + partId));
        return toAdminPartResponse(part);
    }

    /**
     * Create a new listening part along with its questions and question options.
     */
    @Transactional
    public AdminListeningPartResponse createPart(AdminListeningPartRequest request, String username) {
        ListeningPart part = ListeningPart.builder()
                .partNumber(request.getPartNumber())
                .title(request.getTitle())
                .topic(request.getTopic())
                .audioUrl("") // Set empty initially, will be generated
                .audioStatus(AudioStatus.PENDING)
                .transcriptText(request.getTranscriptText())
                .durationSeconds(request.getDurationSeconds() != null ? request.getDurationSeconds() : 180)
                .createdBy(username)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<ListeningQuestion> questions = request.getQuestions().stream()
                .map(q -> {
                    ListeningQuestion question = ListeningQuestion.builder()
                            .part(part)
                            .questionType(QuestionType.valueOf(q.getQuestionType().toUpperCase()))
                            .questionText(q.getQuestionText())
                            .correctAnswer(q.getCorrectAnswer().trim())
                            .orderIndex(q.getOrderIndex())
                            .build();

                    List<QuestionOption> options = new ArrayList<>();
                    if (question.getQuestionType() == QuestionType.MCQ && q.getOptions() != null) {
                        int optIndex = 1;
                        for (AdminListeningPartRequest.QuestionRequest.OptionRequest optReq : q.getOptions()) {
                            options.add(QuestionOption.builder()
                                    .listeningQuestion(question)
                                    .label(optReq.getLabel())
                                    .content(optReq.getContent())
                                    .isCorrect(q.getCorrectAnswer().equalsIgnoreCase(optReq.getLabel()))
                                    .orderIndex(optIndex++)
                                    .build());
                        }
                    }
                    question.setOptions(options);
                    return question;
                })
                .collect(Collectors.toList());

        part.setQuestions(questions);
        ListeningPart saved = partRepository.save(part);

        // Trigger async audio generation asynchronously
        audioGenerationService.generateAudioAsync(saved.getPartId());

        return toAdminPartResponse(saved);
    }

    /**
     * Update an existing listening part's details, questions, and options.
     */
    @Transactional
    public AdminListeningPartResponse updatePart(Long partId, AdminListeningPartRequest request, String username) {
        ListeningPart part = partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening part not found: " + partId));

        boolean scriptChanged = part.getTranscriptText() == null || 
                !part.getTranscriptText().equals(request.getTranscriptText());

        part.setPartNumber(request.getPartNumber());
        part.setTitle(request.getTitle());
        part.setTopic(request.getTopic());
        part.setTranscriptText(request.getTranscriptText());
        part.setDurationSeconds(request.getDurationSeconds() != null ? request.getDurationSeconds() : 180);
        part.setUpdatedAt(LocalDateTime.now());

        // Clear and replace questions/options
        part.getQuestions().clear();

        List<ListeningQuestion> questions = request.getQuestions().stream()
                .map(q -> {
                    ListeningQuestion question = ListeningQuestion.builder()
                            .part(part)
                            .questionType(QuestionType.valueOf(q.getQuestionType().toUpperCase()))
                            .questionText(q.getQuestionText())
                            .correctAnswer(q.getCorrectAnswer().trim())
                            .orderIndex(q.getOrderIndex())
                            .build();

                    List<QuestionOption> options = new ArrayList<>();
                    if (question.getQuestionType() == QuestionType.MCQ && q.getOptions() != null) {
                        int optIndex = 1;
                        for (AdminListeningPartRequest.QuestionRequest.OptionRequest optReq : q.getOptions()) {
                            options.add(QuestionOption.builder()
                                    .listeningQuestion(question)
                                    .label(optReq.getLabel())
                                    .content(optReq.getContent())
                                    .isCorrect(q.getCorrectAnswer().equalsIgnoreCase(optReq.getLabel()))
                                    .orderIndex(optIndex++)
                                    .build());
                        }
                    }
                    question.setOptions(options);
                    return question;
                })
                .collect(Collectors.toList());

        part.getQuestions().addAll(questions);

        // If the transcript changes or the audio status is not READY, regenerate TTS audio
        if (scriptChanged || part.getAudioStatus() != AudioStatus.READY) {
            part.setAudioStatus(AudioStatus.PENDING);
            partRepository.saveAndFlush(part);
            audioGenerationService.generateAudioAsync(part.getPartId());
        } else {
            partRepository.save(part);
        }

        return toAdminPartResponse(part);
    }

    /**
     * Delete a listening part and clean up associated audio object from MinIO.
     */
    @Transactional
    public void deletePart(Long partId) {
        ListeningPart part = partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening part not found: " + partId));

        // Delete audio from MinIO
        String audioUrl = part.getAudioUrl();
        if (audioUrl != null && audioUrl.contains("/audio/")) {
            String key = audioUrl.substring(audioUrl.lastIndexOf("/audio/") + 7);
            storageService.deleteAudio(key);
        }

        partRepository.delete(part);
    }

    /**
     * Manually trigger regeneration of TTS audio for a listening part.
     */
    @Transactional
    public void regenerateAudio(Long partId) {
        ListeningPart part = partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening part not found: " + partId));

        part.setAudioStatus(AudioStatus.PENDING);
        partRepository.saveAndFlush(part);
        audioGenerationService.generateAudioAsync(partId);
    }

    /**
     * Find all parts with audioStatus = FAILED and retry generating audio.
     */
    @Transactional
    public void retryFailedAudio() {
        List<ListeningPart> failedParts = partRepository.findByAudioStatus(AudioStatus.FAILED);
        log.info("Retrying audio generation for {} failed parts", failedParts.size());
        for (ListeningPart part : failedParts) {
            part.setAudioStatus(AudioStatus.PENDING);
            partRepository.save(part);
            audioGenerationService.generateAudioAsync(part.getPartId());
        }
    }

    /**
     * Get statistics regarding audio status and topic distribution.
     */
    @Transactional(readOnly = true)
    public AdminListeningStatsResponse getStats() {
        List<ListeningPart> allParts = partRepository.findAll();

        Map<String, Long> statusCounts = new HashMap<>();
        Map<String, Long> topicCounts = new HashMap<>();

        for (ListeningPart part : allParts) {
            String status = part.getAudioStatus() != null ? part.getAudioStatus().name() : "PENDING";
            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);

            String topic = part.getTopic() != null ? part.getTopic() : "General";
            topicCounts.put(topic, topicCounts.getOrDefault(topic, 0L) + 1);
        }

        return AdminListeningStatsResponse.builder()
                .statusCounts(statusCounts)
                .topicCounts(topicCounts)
                .totalParts(allParts.size())
                .build();
    }

    // Helper: Map entity to AdminListeningPartResponse
    private AdminListeningPartResponse toAdminPartResponse(ListeningPart part) {
        List<AdminListeningPartResponse.QuestionDto> questionDtos = part.getQuestions().stream()
                .map(q -> AdminListeningPartResponse.QuestionDto.builder()
                        .questionId(q.getQuestionId())
                        .verified(q.getVerified())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .correctAnswer(q.getCorrectAnswer())
                        .orderIndex(q.getOrderIndex())
                        .options(mapOptions(q.getOptions()))
                        .build())
                .collect(Collectors.toList());

        return AdminListeningPartResponse.builder()
                .partId(part.getPartId())
                .partNumber(part.getPartNumber())
                .title(part.getTitle())
                .topic(part.getTopic())
                .audioUrl(part.getAudioUrl())
                .audioStatus(part.getAudioStatus() != null ? part.getAudioStatus().name() : "PENDING")
                .durationSeconds(part.getDurationSeconds())
                .transcriptText(part.getTranscriptText())
                .createdAt(part.getCreatedAt())
                .updatedAt(part.getUpdatedAt())
                .createdBy(part.getCreatedBy())
                .questionCount(questionDtos.size())
                .questions(questionDtos)
                .build();
    }

    // Helper: Map options list to response list
    private List<QuestionOptionResponse> mapOptions(List<QuestionOption> options) {
        if (options == null) return null;
        return options.stream()
                .map(o -> QuestionOptionResponse.builder()
                        .optionId(o.getOptionId())
                        .label(o.getLabel())
                        .content(o.getContent())
                        .isCorrect(o.getIsCorrect())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Parse a sort string like "createdAt,desc" into a Spring Sort object.
     */
    private Sort parseSort(String sortStr, String defaultField) {
        if (sortStr == null || sortStr.isBlank()) {
            return Sort.by(Sort.Direction.DESC, defaultField);
        }
        String[] parts = sortStr.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
            direction = Sort.Direction.ASC;
        }
        return Sort.by(direction, field);
    }
}
