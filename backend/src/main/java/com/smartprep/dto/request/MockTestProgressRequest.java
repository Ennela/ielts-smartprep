package com.smartprep.dto.request;

import com.smartprep.model.enums.SkillType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MockTestProgressRequest {

    @NotNull(message = "Current section is required")
    private SkillType currentSection;

    @NotNull(message = "Time remaining is required")
    private Integer timeRemainingSeconds;

    @NotNull(message = "Progress answers are required")
    private String progressJson; // Raw JSON mapping of questionId -> answer
}
