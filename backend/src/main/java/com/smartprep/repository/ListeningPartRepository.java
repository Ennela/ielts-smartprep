package com.smartprep.repository;

import com.smartprep.model.entity.ListeningPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.smartprep.model.enums.AudioStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface ListeningPartRepository extends JpaRepository<ListeningPart, Long> {
    List<ListeningPart> findByPartNumberOrderByPartIdAsc(Integer partNumber);
    List<ListeningPart> findAllByOrderByPartNumberAscPartIdAsc();

    @Query("SELECT p FROM ListeningPart p WHERE " +
           "(:audioStatus IS NULL OR p.audioStatus = :audioStatus) AND " +
           "(:topic IS NULL OR p.topic = :topic)")
    Page<ListeningPart> findByFilters(
            @Param("audioStatus") AudioStatus audioStatus,
            @Param("topic") String topic,
            Pageable pageable);

    List<ListeningPart> findByAudioStatus(AudioStatus audioStatus);
}
