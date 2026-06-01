package com.smartprep.repository;

import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.enums.SkillType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScoreHistoryRepository extends JpaRepository<ScoreHistory, Long> {

    List<ScoreHistory> findByUserUserIdOrderByRecordedAtDesc(Long userId);

    // Analytics: average score per skill
    @Query("SELECT s.skillType, AVG(s.score), COUNT(s) FROM ScoreHistory s " +
           "WHERE s.user.userId = :userId GROUP BY s.skillType")
    List<Object[]> getSkillAverages(@Param("userId") Long userId);

    // Analytics: score trend (weekly) - native query for YEARWEEK
    @Query(value = "SELECT YEARWEEK(recorded_at, 1) as period, AVG(score) as avg_score " +
           "FROM score_history WHERE user_id = :userId AND skill_type = :skill " +
           "AND recorded_at >= :since " +
           "GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> getWeeklyTrend(@Param("userId") Long userId,
                                   @Param("skill") String skill,
                                   @Param("since") LocalDateTime since);

    // Analytics: score trend (monthly) - native query for DATE_FORMAT
    @Query(value = "SELECT DATE_FORMAT(recorded_at, '%Y-%m') as period, AVG(score) as avg_score " +
           "FROM score_history WHERE user_id = :userId AND skill_type = :skill " +
           "AND recorded_at >= :since " +
           "GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> getMonthlyTrend(@Param("userId") Long userId,
                                    @Param("skill") String skill,
                                    @Param("since") LocalDateTime since);

    // Paginated history across all skills
    Page<ScoreHistory> findByUserUserIdOrderByRecordedAtDesc(Long userId, Pageable pageable);

    // Paginated history filtered by skill
    Page<ScoreHistory> findByUserUserIdAndSkillTypeOrderByRecordedAtDesc(
            Long userId, SkillType skillType, Pageable pageable);

    // Admin: count tests today
    long countByRecordedAtAfter(LocalDateTime since);
}
