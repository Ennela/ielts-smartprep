package com.smartprep.repository;

import com.smartprep.model.entity.ListeningQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListeningQuestionRepository extends JpaRepository<ListeningQuestion, Long> {
}
