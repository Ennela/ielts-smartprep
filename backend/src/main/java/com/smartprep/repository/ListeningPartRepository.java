package com.smartprep.repository;

import com.smartprep.model.entity.ListeningPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListeningPartRepository extends JpaRepository<ListeningPart, Long> {
    List<ListeningPart> findByPartNumberOrderByPartIdAsc(Integer partNumber);
    List<ListeningPart> findAllByOrderByPartNumberAscPartIdAsc();
}
