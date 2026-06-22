package com.smartprep.repository;

import com.smartprep.model.entity.ReadingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReadingQuestionRepository extends JpaRepository<ReadingQuestion, Long> {
}
