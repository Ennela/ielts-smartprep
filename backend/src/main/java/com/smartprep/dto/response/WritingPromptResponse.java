package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingPromptResponse {

    private Long promptId;
    private String promptText;
    private String essayType;
    private String imageUrl;
    private String visualData;
}
