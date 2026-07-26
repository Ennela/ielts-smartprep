package com.smartprep.service.vocab;

import com.smartprep.model.enums.SkillType;

public interface VocabSourceResolver {
    SkillType getSkillType();
    /**
     * Source text is fed to the AI and echoed back to the caller, so every
     * implementation must scope the lookup to the requesting user.
     */
    String resolveSourceText(Long userId, Long sourceId);
}
