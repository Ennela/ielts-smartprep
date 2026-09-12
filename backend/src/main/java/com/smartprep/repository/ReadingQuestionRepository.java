package com.smartprep.repository;

import com.smartprep.model.entity.ReadingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReadingQuestionRepository extends JpaRepository<ReadingQuestion, Long> {

    /** {@code [quizId, questionCount]} for each quiz in the set -- one query for a whole page. */
    @Query("SELECT q.quiz.quizId, COUNT(q) FROM ReadingQuestion q WHERE q.quiz.quizId IN :quizIds GROUP BY q.quiz.quizId")
    List<Object[]> countByQuizIds(@Param("quizIds") Collection<Long> quizIds);
}
