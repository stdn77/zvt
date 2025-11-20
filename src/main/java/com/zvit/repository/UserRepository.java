package com.zvit.repository;

import com.zvit.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByPhoneHash(String phoneHash);
    
    Optional<User> findByEmailHash(String emailHash);
    
    boolean existsByPhoneHash(String phoneHash);
    
    boolean existsByEmailHash(String emailHash);
}

