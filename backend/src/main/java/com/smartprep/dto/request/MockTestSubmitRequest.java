package com.smartprep.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MockTestSubmitRequest {

    @NotNull(message = "Submission answers are required")
    private String progressJson; // Final dump of all questionId -> answer mappings
}
