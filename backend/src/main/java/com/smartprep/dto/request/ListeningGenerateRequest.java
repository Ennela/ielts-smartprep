package com.smartprep.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListeningGenerateRequest {

    @Min(value = 1, message = "Part number must be between 1 and 4")
    @Max(value = 4, message = "Part number must be between 1 and 4")
    private Integer partNumber;

    @Size(max = 100, message = "Topic must not exceed 100 characters")
    private String topic;

    private Boolean adaptive;
}
