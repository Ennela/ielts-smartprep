package com.smartprep.service.ai;

import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.repository.WritingPromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The one short transaction that writes a generated Task 1 / Task 2 prompt pair.
 *
 * <p>Its own bean rather than a private method on {@link WritingGenerationService} because
 * Spring applies {@code @Transactional} through a proxy: a call from one method of a bean to
 * another method of the same bean does not pass through it, so the annotation would read
 * correctly and do nothing.
 *
 * <p>The pair is written together because it is generated together — a failure saving Task 2
 * would otherwise leave an orphaned Task 1 prompt that no exam references.
 */
@Service
@RequiredArgsConstructor
public class WritingPromptPersistence {

    private final WritingPromptRepository promptRepository;

    @Transactional
    public List<WritingPrompt> savePair(WritingPrompt task1, WritingPrompt task2) {
        return List.of(promptRepository.save(task1), promptRepository.save(task2));
    }
}
