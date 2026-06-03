package com.shopee.monolith.modules.user.repository;

import com.shopee.monolith.modules.user.entity.VerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from VerificationToken t where t.tokenHash = :tokenHash")
    Optional<VerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<VerificationToken> findByTokenHash(String tokenHash);
}
