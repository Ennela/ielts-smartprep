package com.smartprep.dto.response;

import com.smartprep.model.enums.ContentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentItemResponse {

    private Long id;
    private String type;
    private String title;
    private ContentStatus contentStatus;
    private String createdBy;
    private String source;
    private LocalDateTime createdAt;
}
