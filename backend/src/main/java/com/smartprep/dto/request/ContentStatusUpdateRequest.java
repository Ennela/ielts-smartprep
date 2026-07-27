package com.smartprep.dto.request;

import com.smartprep.model.enums.ContentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentStatusUpdateRequest {

    @NotNull(message = "newStatus is required")
    private ContentStatus newStatus;
}
