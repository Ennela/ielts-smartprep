package com.smartprep.repository;

import com.smartprep.model.entity.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {

    List<UserAnswer> findByScoreHistoryHistoryIdOrderByQuestionNoAsc(Long historyId);
}
