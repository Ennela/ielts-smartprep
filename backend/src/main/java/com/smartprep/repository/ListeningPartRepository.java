package com.smartprep.repository;

import com.smartprep.model.entity.ListeningPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListeningPartRepository extends JpaRepository<ListeningPart, Long> {
    @Override
    @Query("SELECT p FROM ListeningPart p WHERE p.partId = :id AND p.deletedAt IS NULL")
    Optional<ListeningPart> findById(@Param("id") Long id);

    @Query("SELECT p FROM ListeningPart p WHERE p.partId = :id")
    Optional<ListeningPart> findIncludingDeletedById(@Param("id") Long id);

    @Override
    @Query("SELECT p FROM ListeningPart p WHERE p.deletedAt IS NULL")
    List<ListeningPart> findAll();

    @Override
    @Query("SELECT p FROM ListeningPart p WHERE p.deletedAt IS NULL")
    Page<ListeningPart> findAll(Pageable pageable);

    @Query("SELECT p FROM ListeningPart p WHERE p.partNumber = :partNumber " +
           "AND p.deletedAt IS NULL ORDER BY p.partId ASC")
    List<ListeningPart> findByPartNumberOrderByPartIdAsc(
            @Param("partNumber") Integer partNumber);

    @Query("SELECT p FROM ListeningPart p WHERE p.deletedAt IS NULL " +
           "ORDER BY p.partNumber ASC, p.partId ASC")
    List<ListeningPart> findAllByOrderByPartNumberAscPartIdAsc();

    @Query("SELECT p FROM ListeningPart p WHERE " +
           "(:audioStatus IS NULL OR p.audioStatus = :audioStatus) AND " +
           "(:topic IS NULL OR p.topic = :topic) AND p.deletedAt IS NULL")
    Page<ListeningPart> findByFilters(
            @Param("audioStatus") AudioStatus audioStatus,
            @Param("topic") String topic,
            Pageable pageable);

    @Query("SELECT p FROM ListeningPart p WHERE p.audioStatus = :audioStatus " +
           "AND p.deletedAt IS NULL")
    List<ListeningPart> findByAudioStatus(@Param("audioStatus") AudioStatus audioStatus);

    @Query("SELECT p FROM ListeningPart p WHERE p.contentStatus = :contentStatus " +
           "AND p.deletedAt IS NULL")
    Page<ListeningPart> findByContentStatus(
            @Param("contentStatus") ContentStatus contentStatus,
            Pageable pageable);
}
