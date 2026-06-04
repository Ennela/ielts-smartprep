package com.smartprep.repository;

import com.smartprep.model.entity.MockTestSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockTestSectionRepository extends JpaRepository<MockTestSection, Long> {
}
