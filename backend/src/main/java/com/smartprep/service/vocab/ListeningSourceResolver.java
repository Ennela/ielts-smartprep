package com.smartprep.service.vocab;

import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ListeningTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListeningSourceResolver implements VocabSourceResolver {

    private final ListeningTestRepository listeningTestRepository;

    @Override
    public SkillType getSkillType() {
        return SkillType.LISTENING;
    }

    @Override
    public String resolveSourceText(Long sourceId) {
        ListeningTest test = listeningTestRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening test not found with ID: " + sourceId));

        if (test.getTestParts() == null || test.getTestParts().isEmpty()) {
            return "";
        }

        return test.getTestParts().stream()
                .map(tp -> tp.getPart())
                .filter(Objects::nonNull)
                .map(part -> part.getTranscriptText())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n"));
    }
}
