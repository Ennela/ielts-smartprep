package com.smartprep.repository;

import com.smartprep.model.entity.ListeningQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ListeningQuestionRepository extends JpaRepository<ListeningQuestion, Long> {

    /** The question with its part loaded, for callers that read the part outside a transaction. */
    @EntityGraph(attributePaths = "part")
    Optional<ListeningQuestion> findWithPartByQuestionId(Long questionId);
}
