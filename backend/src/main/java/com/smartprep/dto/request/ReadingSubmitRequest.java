package com.smartprep.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSubmitRequest {

    @NotEmpty(message = "Answers cannot be empty")
    private Map<Long, String> answers; // questionId -> userAnswer
}
