package com.smartprep.service.vocab;

import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.WritingSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WritingSourceResolver implements VocabSourceResolver {

    private final WritingSubmissionRepository writingSubmissionRepository;

    @Override
    public SkillType getSkillType() {
        return SkillType.WRITING;
    }

    @Override
    public String resolveSourceText(Long sourceId) {
        WritingSubmission submission = writingSubmissionRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Writing submission not found with ID: " + sourceId));

        String promptText = (submission.getPrompt() != null) ? submission.getPrompt().getPromptText() : "";
        String essayText = (submission.getEssayText() != null) ? submission.getEssayText() : "";

        return "Prompt:\n" + promptText + "\n\nStudent Essay:\n" + essayText;
    }
}
