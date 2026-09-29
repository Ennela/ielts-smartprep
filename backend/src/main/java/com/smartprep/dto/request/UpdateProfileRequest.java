package com.smartprep.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateProfileRequest {

    /**
     * Bounds are on every target score because the column is DECIMAL(2,1).
     * A value of 12 was accepted by validation and then truncated or rejected by MySQL,
     * which surfaced as a 500 rather than as "that is not a band score". The slider in
     * the interface only offers 0 to 9; this makes the API agree with it.
     */
    private static final String MIN_BAND = "0.0";
    private static final String MAX_BAND = "9.0";

    @NotBlank
    @Size(max = 100, message = "Display name must be at most 100 characters")
    private String displayName;

    @Size(max = 255)
    private String avatarUrl;

    @NotNull
    @DecimalMin(value = MIN_BAND, message = "Target band must be between 0 and 9")
    @DecimalMax(value = MAX_BAND, message = "Target band must be between 0 and 9")
    private BigDecimal targetReadingScore;

    @NotNull
    @DecimalMin(value = MIN_BAND, message = "Target band must be between 0 and 9")
    @DecimalMax(value = MAX_BAND, message = "Target band must be between 0 and 9")
    private BigDecimal targetWritingScore;

    @NotNull
    @DecimalMin(value = MIN_BAND, message = "Target band must be between 0 and 9")
    @DecimalMax(value = MAX_BAND, message = "Target band must be between 0 and 9")
    private BigDecimal targetListeningScore;

    /**
     * Nullable on purpose: a client that does not know about this field must not be
     * treated as having switched the preference off.
     */
    private Boolean emailNotifications;
}
