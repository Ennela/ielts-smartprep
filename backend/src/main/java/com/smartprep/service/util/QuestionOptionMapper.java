package com.smartprep.service.util;

import com.smartprep.dto.response.QuestionOptionResponse;
import com.smartprep.model.entity.QuestionOption;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Single place that decides whether QuestionOption.isCorrect reaches the client.
 *
 * mapForExam   - question-paper view (quiz not submitted yet, mock test session): never carries the answer key.
 * mapForReview - result/review view (after submit) and admin previews: carries the answer key.
 *
 * There is deliberately no boolean parameter: mapOptions(options, true) at the wrong
 * call site was a silent answer leak that no test could catch.
 */
public class QuestionOptionMapper {

    public static List<QuestionOptionResponse> mapForExam(List<QuestionOption> options) {
        if (options == null) return null;
        return options.stream()
                .map(o -> QuestionOptionResponse.builder()
                        .optionId(o.getOptionId())
                        .label(o.getLabel())
                        .content(o.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    public static List<QuestionOptionResponse> mapForReview(List<QuestionOption> options) {
        if (options == null) return null;
        return options.stream()
                .map(o -> QuestionOptionResponse.builder()
                        .optionId(o.getOptionId())
                        .label(o.getLabel())
                        .content(o.getContent())
                        .isCorrect(o.getIsCorrect())
                        .build())
                .collect(Collectors.toList());
    }
}
