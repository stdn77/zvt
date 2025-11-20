package com.zvit.repository;

import com.zvit.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {

    List<Report> findByGroup_IdAndUser_IdOrderBySubmittedAtDesc(String groupId, String userId);

    Optional<Report> findFirstByGroup_IdAndUser_IdOrderBySubmittedAtDesc(String groupId, String userId);

    List<Report> findByGroup_IdOrderBySubmittedAtDesc(String groupId);

    /**
     * Оптимізований запит для отримання останніх звітів всіх користувачів групи
     * Використовується для вирішення N+1 проблеми в getGroupStatuses
     */
    @Query("SELECT r FROM Report r " +
           "JOIN FETCH r.user " +
           "WHERE r.id IN (" +
           "  SELECT MAX(r2.id) FROM Report r2 " +
           "  WHERE r2.group.id = :groupId " +
           "  GROUP BY r2.user.id" +
           ")")
    List<Report> findLatestReportsByGroupId(@Param("groupId") String groupId);
}