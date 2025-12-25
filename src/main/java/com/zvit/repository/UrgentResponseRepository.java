package com.zvit.repository;

import com.zvit.entity.UrgentResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UrgentResponseRepository extends JpaRepository<UrgentResponse, String> {

    /**
     * Знайти всі відповіді для термінової сесії
     */
    List<UrgentResponse> findByUrgentSessionId(String urgentSessionId);

    /**
     * Перевірити чи користувач відповів на терміновий запит
     */
    boolean existsByUrgentSessionIdAndUserId(String urgentSessionId, String userId);

    /**
     * Знайти відповідь користувача на терміновий запит
     */
    Optional<UrgentResponse> findByUrgentSessionIdAndUserId(String urgentSessionId, String userId);

    /**
     * Порахувати кількість відповідей для термінової сесії
     */
    long countByUrgentSessionId(String urgentSessionId);

    /**
     * Видалити всі відповіді для термінової сесії (при закритті)
     */
    void deleteByUrgentSessionId(String urgentSessionId);
}
