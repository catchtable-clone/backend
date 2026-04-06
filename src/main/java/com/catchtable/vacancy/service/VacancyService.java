package com.catchtable.vacancy.service;

import com.catchtable.store.entity.Store;
import com.catchtable.remain.StoreRemain;
import com.catchtable.remain.StoreRemainRepository;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.vacancy.dto.VacancyListResponse;
import com.catchtable.vacancy.entity.Vacancy;
import com.catchtable.vacancy.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VacancyService {

    private final VacancyRepository vacancyRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public Long register(Long userId, Long remainId) {
        if (vacancyRepository.existsByUserIdAndRemainIdAndIsDeletedFalse(userId, remainId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 빈자리 알림을 등록했습니다.");
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
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "잔여 좌석 정보를 찾을 수 없습니다."));
                    Store store = storeRepository.findById(storeRemain.getStoreId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매장 정보를 찾을 수 없습니다."));
                    return new VacancyListResponse(vacancy, storeRemain, store);
                })
                .toList();
    }

    @Transactional
    public Long delete(Long vacancyId) {
        Vacancy vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."));
        if (vacancy.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 삭제된 알림입니다.");
        }
        vacancy.delete();
        return vacancy.getId();
    }
}
