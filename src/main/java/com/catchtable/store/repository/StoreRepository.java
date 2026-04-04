package com.catchtable.store.repository;

import com.catchtable.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    @Query("SELECT s FROM Store s WHERE s.storeName LIKE %:name% AND s.isDeleted = false")
    List<Store> searchByStoreName(@Param("name") String name);
}
