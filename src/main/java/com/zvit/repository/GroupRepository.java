package com.zvit.repository;

import com.zvit.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, String> {

    Optional<Group> findByAccessCode(String accessCode);

    boolean existsByAccessCode(String accessCode);
}
