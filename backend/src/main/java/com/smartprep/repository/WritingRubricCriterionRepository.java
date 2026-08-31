package com.smartprep.repository;

import com.smartprep.model.entity.WritingRubricCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WritingRubricCriterionRepository extends JpaRepository<WritingRubricCriterion, Long> {
}
