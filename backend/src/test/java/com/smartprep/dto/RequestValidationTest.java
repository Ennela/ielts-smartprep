package com.smartprep.dto;

import com.smartprep.dto.request.ReadingGenerateRequest;
import com.smartprep.dto.request.WritingGradeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // --- ReadingGenerateRequest ---

    @Test
    @DisplayName("ReadingGenerateRequest - valid input should pass")
    void readingRequest_validInput_noViolations() {
        ReadingGenerateRequest request = new ReadingGenerateRequest("Environment", "EASY");
        Set<ConstraintViolation<ReadingGenerateRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("ReadingGenerateRequest - blank topic should fail")
    void readingRequest_blankTopic_hasViolation() {
        ReadingGenerateRequest request = new ReadingGenerateRequest("", "EASY");
        Set<ConstraintViolation<ReadingGenerateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("ReadingGenerateRequest - topic with special chars should fail")
    void readingRequest_specialCharsTopic_hasViolation() {
        ReadingGenerateRequest request = new ReadingGenerateRequest("Topic; DROP TABLE users;", "EASY");
        Set<ConstraintViolation<ReadingGenerateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("ReadingGenerateRequest - topic exceeding 100 chars should fail")
    void readingRequest_longTopic_hasViolation() {
        String longTopic = "A".repeat(101);
        ReadingGenerateRequest request = new ReadingGenerateRequest(longTopic, "EASY");
        Set<ConstraintViolation<ReadingGenerateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    // --- WritingGradeRequest ---

    @Test
    @DisplayName("WritingGradeRequest - valid input should pass")
    void writingRequest_validInput_noViolations() {
        WritingGradeRequest request = new WritingGradeRequest(1L, "This is my essay text.");
        Set<ConstraintViolation<WritingGradeRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("WritingGradeRequest - essay exceeding 10000 chars should fail")
    void writingRequest_longEssay_hasViolation() {
        String longEssay = "A".repeat(10001);
        WritingGradeRequest request = new WritingGradeRequest(1L, longEssay);
        Set<ConstraintViolation<WritingGradeRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("WritingGradeRequest - null promptId should fail")
    void writingRequest_nullPromptId_hasViolation() {
        WritingGradeRequest request = new WritingGradeRequest(null, "Essay text");
        Set<ConstraintViolation<WritingGradeRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }
}
