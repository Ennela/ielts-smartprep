package com.smartprep.repository;

import com.smartprep.model.entity.ReadingQuiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingQuizRepository extends JpaRepository<ReadingQuiz, Long> {

    List<ReadingQuiz> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ReadingQuiz> findByQuizIdAndUserUserId(Long quizId, Long userId);
}
