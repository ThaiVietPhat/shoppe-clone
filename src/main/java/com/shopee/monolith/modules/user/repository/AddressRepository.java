package com.shopee.monolith.modules.user.repository;

import com.shopee.monolith.modules.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findAllByUserIdOrderByIsDefaultDesc(UUID userId);

    @Query("select a from Address a where a.userId = :userId and a.isDefault = true")
    Optional<Address> findDefaultByUserId(@Param("userId") UUID userId);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update Address a set a.isDefault = false where a.userId = :userId")
    void resetDefaultAddresses(@Param("userId") UUID userId);
}
