package com.catchtable.vacancy.service;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.store.entity.Store;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Transactional
    public Long register(Long userId, Long remainId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        StoreRemain storeRemain = storeRemainRepository.findById(remainId)
                .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));

        if (storeRemain.getRemainTeam() > 0) {
            throw new CustomException(ErrorCode.VACANCY_REMAIN_NOT_EXHAUSTED);
        }

        if (vacancyRepository.existsByUser_IdAndStoreRemain_IdAndIsDeletedFalse(userId, remainId)) {
            throw new CustomException(ErrorCode.VACANCY_ALREADY_REGISTERED);
        }
        
        Vacancy vacancy = new Vacancy(user, storeRemain);
        return vacancyRepository.save(vacancy).getId();
    }

    @Transactional(readOnly = true)
    public List<VacancyListResponse> getMyList(Long userId) {
        // N+1 문제 해결: EntityGraph를 통해 StoreRemain과 Store를 함께 조회 (1번의 쿼리로 처리됨)
        return vacancyRepository.findByUser_IdAndIsDeletedFalse(userId)
                .stream()
                .map(vacancy -> {
                    StoreRemain storeRemain = vacancy.getStoreRemain();
                    Store store = storeRemain.getStore();
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
