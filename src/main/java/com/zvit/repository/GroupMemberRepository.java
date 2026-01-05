package com.zvit.repository;

import com.zvit.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, String> {

    List<GroupMember> findByUserId(String userId);

    List<GroupMember> findByGroupId(String groupId);

    List<GroupMember> findByGroupIdAndStatus(String groupId, GroupMember.MemberStatus status);

    Optional<GroupMember> findByGroupIdAndUserId(String groupId, String userId);

    long countByGroupId(String groupId);

    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END " +
           "FROM GroupMember gm " +
           "WHERE gm.group.id = :groupId " +
           "AND gm.user.id = :userId " +
           "AND gm.role = com.zvit.entity.GroupMember$Role.ADMIN")
    boolean isUserAdminOfGroup(@Param("groupId") String groupId, @Param("userId") String userId);

    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END " +
           "FROM GroupMember gm " +
           "WHERE gm.group.id = :groupId " +
           "AND gm.user.id = :userId " +
           "AND gm.role IN (com.zvit.entity.GroupMember$Role.ADMIN, com.zvit.entity.GroupMember$Role.MODER)")
    boolean isUserAdminOrModerOfGroup(@Param("groupId") String groupId, @Param("userId") String userId);

    /**
     * Видаляє всі pending запити старші за вказану дату
     */
    @Modifying
    @Query("DELETE FROM GroupMember gm " +
           "WHERE gm.status = com.zvit.entity.GroupMember$MemberStatus.PENDING " +
           "AND gm.joinedAt < :cutoffTime")
    int deleteExpiredPendingMembers(@Param("cutoffTime") LocalDateTime cutoffTime);
}
