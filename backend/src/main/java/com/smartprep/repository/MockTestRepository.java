package com.smartprep.repository;

import com.smartprep.model.entity.MockTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockTestRepository extends JpaRepository<MockTest, Long> {
}
