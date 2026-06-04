package com.smartprep.service.vocab;

import com.smartprep.model.enums.SkillType;

public interface VocabSourceResolver {
    SkillType getSkillType();
    String resolveSourceText(Long sourceId);
}
