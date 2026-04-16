package com.catchtable.vacancy.service;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.store.entity.Store;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.vacancy.dto.write.VacancyListResponse;
import com.catchtable.vacancy.entity.Vacancy;
import com.catchtable.vacancy.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VacancyService {

    private final VacancyRepository vacancyRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public Long register(Long userId, Long remainId) {
        StoreRemain storeRemain = storeRemainRepository.findById(remainId)
                .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));

        if (storeRemain.getRemainTeam() > 0) {
            throw new CustomException(ErrorCode.VACANCY_REMAIN_NOT_EXHAUSTED);
        }

        if (vacancyRepository.existsByUserIdAndRemainIdAndIsDeletedFalse(userId, remainId)) {
            throw new CustomException(ErrorCode.VACANCY_ALREADY_REGISTERED);
        }
        Vacancy vacancy = new Vacancy(userId, remainId);
        return vacancyRepository.save(vacancy).getId();
    }

    @Transactional(readOnly = true)
    public List<VacancyListResponse> getMyList(Long userId) {
        // TODO: N+1 문제, vacancy N개 조회 시 storeRemain N번 + store N번 추가 쿼리 발생.
        // 나중에 인증 연동 시 @Query로 JOIN 처리 필요함.
        return vacancyRepository.findByUserIdAndIsDeletedFalse(userId)
                .stream()
                .map(vacancy -> {
                    StoreRemain storeRemain = storeRemainRepository.findById(vacancy.getRemainId())
                            .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));
                    Store store = storeRepository.findById(storeRemain.getStore().getId())
                            .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
                    return new VacancyListResponse(vacancy, storeRemain, store);
                })
                .toList();
    }

    @Transactional
    public Long delete(Long vacancyId) {
        Vacancy vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACANCY_NOT_FOUND));
        if (vacancy.getIsDeleted()) {
            throw new CustomException(ErrorCode.VACANCY_ALREADY_DELETED);
        }
        vacancy.delete();
        return vacancy.getId();
    }
}
