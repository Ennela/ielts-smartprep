package com.smartprep.repository;

import com.smartprep.model.entity.MockTest;
import com.smartprep.model.enums.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockTestRepository extends JpaRepository<MockTest, Long> {

    Page<MockTest> findByContentStatus(ContentStatus contentStatus, Pageable pageable);
}
