package com.catchtable.vacancy.repository;

import com.catchtable.vacancy.entity.Vacancy;
import com.catchtable.vacancy.entity.VacancyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    boolean existsByUserIdAndRemainIdAndIsDeletedFalse(Long userId, Long remainId);

    List<Vacancy> findByUserIdAndIsDeletedFalse(Long userId);

    /**
     * 사용자 알림 목록 조회용 — Vacancy + StoreRemain + Store를 단일 쿼리로 가져온다.
     * Vacancy.remainId(Long FK)에는 직접 매핑이 없으므로, StoreRemain을 JOIN으로 묶고
     * StoreRemain.store까지 JOIN FETCH 한다.
     */
    @Query("""
            SELECT v, sr, s
            FROM Vacancy v
            JOIN StoreRemain sr ON sr.id = v.remainId
            JOIN sr.store s
            WHERE v.userId = :userId
              AND v.isDeleted = false
            """)
    List<Object[]> findMyListWithStore(@Param("userId") Long userId);

    List<Vacancy> findByRemainIdAndStatusAndIsDeletedFalse(Long remainId, VacancyStatus status);
}
