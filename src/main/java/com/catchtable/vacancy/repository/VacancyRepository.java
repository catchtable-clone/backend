package com.catchtable.vacancy.repository;

import com.catchtable.vacancy.entity.Vacancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    boolean existsByUserIdAndRemainIdAndIsDeletedFalse(Long userId, Long remainId);

    List<Vacancy> findByUserIdAndIsDeletedFalse(Long userId);
}
