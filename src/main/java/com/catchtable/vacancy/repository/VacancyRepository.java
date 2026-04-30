package com.catchtable.vacancy.repository;

import com.catchtable.vacancy.entity.Vacancy;
import com.catchtable.vacancy.entity.VacancyStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    boolean existsByUser_IdAndStoreRemain_IdAndIsDeletedFalse(Long userId, Long remainId);

    // 내 빈자리 알림 목록 조회 (N+1 방지를 위해 StoreRemain, Store 함께 조회)
    @EntityGraph(attributePaths = {"storeRemain", "storeRemain.store"})
    List<Vacancy> findByUser_IdAndIsDeletedFalse(Long userId);

    List<Vacancy> findByStoreRemain_IdAndStatusAndIsDeletedFalse(Long remainId, VacancyStatus status);

    // 알림 발송 시 구독자 조회 (N+1 방지를 위해 User 함께 조회)
    @EntityGraph(attributePaths = {"user"})
    List<Vacancy> findWithUserByStoreRemain_IdAndStatusAndIsDeletedFalse(Long remainId, VacancyStatus status);
}
