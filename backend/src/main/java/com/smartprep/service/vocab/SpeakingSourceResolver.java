package com.smartprep.service.vocab;

import com.smartprep.model.enums.SkillType;
import org.springframework.stereotype.Component;

@Component
public class SpeakingSourceResolver implements VocabSourceResolver {

    @Override
    public SkillType getSkillType() {
        return SkillType.SPEAKING;
    }

    @Override
    public String resolveSourceText(Long sourceId) {
        // Return a mock speaking topic and transcript to satisfy the 4-skills interface.
        return "Speaking Topic:\n" +
                "Describe a clean and well-preserved natural place that you visited and liked.\n" +
                "You should say: where it is, when you went there, what you did there, and explain why you liked it.\n\n" +
                "Candidate Transcript:\n" +
                "Well, I would like to describe a breathtaking national park located in the northern mountainous area. " +
                "I visited this pristine sanctuary last summer to escape the hustle and bustle of city life. " +
                "The scenery there was absolutely spectacular, featuring lush valleys and crystal-clear streams. " +
                "We hiked along the rugged trails, observed the diverse flora and fauna, and set up a camp near a quiet lake. " +
                "I was deeply impressed by how well-preserved the ecosystem was. Local authorities enforce strict rules " +
                "against littering, which keeps the environment immaculate. It was truly a therapeutic experience " +
                "to connect with nature in such an undisturbed wilderness.";
    }
}
