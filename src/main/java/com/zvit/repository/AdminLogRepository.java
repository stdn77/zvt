package com.zvit.repository;

import com.zvit.entity.AdminLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

    @Query("SELECT a FROM AdminLog a WHERE " +
           "(:dateFrom IS NULL OR a.logDate >= :dateFrom) AND " +
           "(:dateTo IS NULL OR a.logDate <= :dateTo) AND " +
           "(:userName IS NULL OR LOWER(a.userName) LIKE LOWER(CONCAT('%', :userName, '%'))) AND " +
           "(:phoneNumber IS NULL OR a.phoneNumber LIKE CONCAT('%', :phoneNumber, '%')) AND " +
           "(:ipAddress IS NULL OR a.ipAddress LIKE CONCAT('%', :ipAddress, '%')) AND " +
           "(:logLevel IS NULL OR a.logLevel = :logLevel) AND " +
           "(:requestMethod IS NULL OR a.requestMethod = :requestMethod) AND " +
           "(:statusExact IS NULL OR a.responseStatus = :statusExact) AND " +
           "(:statusMin IS NULL OR a.responseStatus >= :statusMin) AND " +
           "(:statusMax IS NULL OR a.responseStatus <= :statusMax) " +
           "ORDER BY a.logDate DESC, a.logTime DESC")
    Page<AdminLog> findByFilters(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("userName") String userName,
            @Param("phoneNumber") String phoneNumber,
            @Param("ipAddress") String ipAddress,
            @Param("logLevel") AdminLog.LogLevel logLevel,
            @Param("requestMethod") String requestMethod,
            @Param("statusExact") Integer statusExact,
            @Param("statusMin") Integer statusMin,
            @Param("statusMax") Integer statusMax,
            Pageable pageable
    );

    void deleteByLogDateBefore(LocalDate date);
}
