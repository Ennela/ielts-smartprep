package com.smartprep.service.vocab;

import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ReadingQuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReadingSourceResolver implements VocabSourceResolver {

    private final ReadingQuizRepository readingQuizRepository;

    @Override
    public SkillType getSkillType() {
        return SkillType.READING;
    }

    @Override
    public String resolveSourceText(Long sourceId) {
        ReadingQuiz quiz = readingQuizRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Reading quiz not found with ID: " + sourceId));
        return quiz.getPassageText() != null ? quiz.getPassageText() : "";
    }
}
