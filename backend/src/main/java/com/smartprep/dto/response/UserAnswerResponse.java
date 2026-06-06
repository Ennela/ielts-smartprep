package com.smartprep.dto.response;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAnswerResponse {

    private Long answerId;
    private Integer questionNo;
    private String questionText;
    private String questionType;
    private String userAnswer;
    private String correctAnswer;
    private Boolean isCorrect;
    private String explanation;
    private String optionsJson;
    private String evidenceText;
    private Integer evidenceOffset;
    private Integer evidenceLength;
}
