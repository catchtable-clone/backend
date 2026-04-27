package com.catchtable.store.repository;

import com.catchtable.store.entity.District;
import com.catchtable.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT s FROM Store s WHERE s.storeName LIKE %:name% AND s.isDeleted = false")
    List<Store> searchByStoreName(@Param("name") String name);

    List<Store> findAllByDistrictAndIsDeletedFalse(District district);

    @Modifying
    @Query("UPDATE Store s SET s.reviewCount = s.reviewCount + 1 WHERE s.id = :storeId")
    void increaseReviewCount(@Param("storeId") Long storeId);

    @Modifying
    @Query("UPDATE Store s SET s.reviewCount = s.reviewCount - 1 WHERE s.id = :storeId AND s.reviewCount > 0")
    void decreaseReviewCount(@Param("storeId") Long storeId);
}
