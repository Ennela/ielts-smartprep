package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VocabReviewRequest {

    @NotBlank(message = "Review grade is required")
    private String grade; // AGAIN, HARD, GOOD, EASY
}
