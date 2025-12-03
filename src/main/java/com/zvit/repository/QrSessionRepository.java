package com.zvit.repository;

import com.zvit.entity.QrSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface QrSessionRepository extends JpaRepository<QrSession, Long> {

    Optional<QrSession> findBySessionToken(String sessionToken);

    @Modifying
    @Query("DELETE FROM QrSession q WHERE q.expiresAt < :now")
    void deleteExpiredSessions(LocalDateTime now);
}
