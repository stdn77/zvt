package com.zvit.repository;

import com.zvit.entity.RsaKeyPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RsaKeyPairRepository extends JpaRepository<RsaKeyPair, Long> {

    /**
     * Знаходить активну пару ключів
     */
    Optional<RsaKeyPair> findByActiveTrue();
}
