package com.smartprep.repository;

import com.smartprep.model.entity.MockTest;
import com.smartprep.model.enums.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockTestRepository extends JpaRepository<MockTest, Long> {

    @Override
    @Query("SELECT m FROM MockTest m WHERE m.mockTestId = :id AND m.deletedAt IS NULL")
    Optional<MockTest> findById(@Param("id") Long id);

    @Query("SELECT m FROM MockTest m WHERE m.mockTestId = :id")
    Optional<MockTest> findIncludingDeletedById(@Param("id") Long id);

    @Override
    @Query("SELECT m FROM MockTest m WHERE m.deletedAt IS NULL")
    List<MockTest> findAll();

    @Override
    @Query("SELECT m FROM MockTest m WHERE m.deletedAt IS NULL")
    Page<MockTest> findAll(Pageable pageable);

    @Query("SELECT m FROM MockTest m WHERE m.contentStatus = :contentStatus " +
           "AND m.deletedAt IS NULL")
    Page<MockTest> findByContentStatus(
            @Param("contentStatus") ContentStatus contentStatus,
            Pageable pageable);
}
