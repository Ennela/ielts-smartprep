package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateProfileRequest {
    @NotBlank
    private String displayName;

    private String avatarUrl;

    @NotNull
    private BigDecimal targetReadingScore;

    @NotNull
    private BigDecimal targetWritingScore;

    @NotNull
    private BigDecimal targetListeningScore;
}
