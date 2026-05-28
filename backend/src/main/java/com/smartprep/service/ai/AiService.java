package com.smartprep.service.ai;

public interface AiService {
    String generateReadingPassage(String topic, String difficulty);
    String gradeWritingEssay(String promptText, String essayText);
    String analyzeListeningError(String transcript, String question, String correctAnswer, String userAnswer);
    String extractVocabulary(String transcript);
}
