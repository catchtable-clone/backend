package com.catchtable.store.service;

import com.catchtable.store.dto.create.StoreCreateRequest;
import com.catchtable.store.dto.create.StoreCreateResponse;
import com.catchtable.store.dto.read.StoreDetailResponse;
import com.catchtable.store.dto.read.StoreListResponse;
import com.catchtable.store.dto.status.StoreStatusUpdateRequest;
import com.catchtable.store.dto.status.StoreStatusUpdateResponse;
import com.catchtable.store.dto.update.StoreUpdateRequest;
import com.catchtable.store.dto.update.StoreUpdateResponse;
import com.catchtable.remain.dto.read.RemainDateResponse;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.store.entity.Category;
import com.catchtable.store.entity.District;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.user.repository.UserRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;

    // 매장 등록
    @CacheEvict(value = "storeList", allEntries = true)
    @Transactional
    public StoreCreateResponse createStore(Long userId, StoreCreateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_STORE_CREATE);
        Store store = request.toEntity();
        Store saved = storeRepository.save(store);
        return StoreCreateResponse.from(saved.getId());
    }

    /**
     * 매장 목록 통합 조회 (이름·카테고리·지역 옵셔널 필터 + DB 페이지네이션 + 인기 정렬)
     * Specification 사용으로 PostgreSQL+enum 조합에서 :param IS NULL 회피.
     */
    @Cacheable(value = "storeList", key = "(#category?.name() ?: 'ALL') + ':' + (#district?.name() ?: 'ALL') + ':' + #page + ':' + #size", condition = "#name == null || #name.isBlank()")
    @Transactional(readOnly = true)
    public List<StoreListResponse> getStores(String name, Category category, District district, int page, int size) {
        int limitedSize = Math.min(size, 100);
        String trimmedName = (name == null || name.isBlank()) ? null : name.trim();
        Specification<Store> spec = buildStoreSpec(trimmedName, category, district);
        Pageable pageable = PageRequest.of(page, limitedSize, popularitySort());
        return storeRepository.findAll(spec, pageable).getContent().stream()
                .map(StoreListResponse::from)
                .toList();
    }

    // 인기 매장 — Redis 캐시(TTL 5분) 적용, Cache Stampede 방지
    // RedisCacheManager는 sync = true 를 일관되게 지원하지 않음 (UnsupportedOperationException 가능).
    // Cache stampede 방지가 필요하면 Redisson 분산락으로 별도 구현해야 함.
    @Cacheable(value = "popularStores", key = "#limit")
    @Transactional(readOnly = true)
    public List<StoreListResponse> getPopularStores(int limit) {
        return storeRepository.findPopular(PageRequest.of(0, limit)).stream()
                .map(StoreListResponse::from)
                .toList();
    }

    @CacheEvict(value = {"popularStores", "storeList"}, allEntries = true)
    @Transactional
    public void evictPopularStoresCache() {
    }

    @Tool(description = "사용자 주변의 인기 매장을 조회합니다. '내 주변 맛집', '근처 인기 매장', '주변 맛집 추천' 등의 요청에 사용하세요. 위치 정보가 없으면 사용할 수 없습니다.")
    @Transactional(readOnly = true)
    public String getNearbyPopularStoresForAi(ToolContext toolContext) {
        Object lat = toolContext.getContext().get("latitude");
        Object lon = toolContext.getContext().get("longitude");

        if (lat == null || lon == null) {
            return "위치 정보가 없어 주변 맛집을 조회할 수 없습니다. 위치 권한을 허용한 후 다시 시도해주세요.";
        }

        double latitude = ((Number) lat).doubleValue();
        double longitude = ((Number) lon).doubleValue();
        double radiusMeters = 3000;

        List<Store> stores = storeRepository.findNearbyByPopularity(latitude, longitude, radiusMeters, PageRequest.of(0, 10));

        if (stores.isEmpty()) {
            return "3km 이내에 등록된 매장이 없습니다.";
        }

        return stores.stream()
                .map(s -> String.format("[%s](/stores/%d) — 별점 %.1f, 리뷰 %d개",
                        s.getStoreName(), s.getId(),
                        s.getAverageStar() != null ? s.getAverageStar() : 0.0,
                        s.getReviewCount() != null ? s.getReviewCount() : 0))
                .collect(Collectors.joining("\n"));
    }

    // 내 주변 매장 (PostGIS GIST 인덱스 + ST_DWithin + KNN 거리 정렬)
    @Transactional(readOnly = true)
    public List<StoreListResponse> getNearbyStores(double latitude, double longitude, int page, int size) {
        int limitedSize = Math.min(size, 100);
        return storeRepository.findNearbyWithGist(
                latitude, longitude, 3000.0,
                PageRequest.of(page, limitedSize)
        ).stream().map(StoreListResponse::from).toList();
    }

    /**
     * 지도 화면 영역 안의 매장을 화면 중심에서 가까운 순서로 조회.
     * limit를 넘으면 가장 먼 매장이 잘려나가므로 화면 중앙 주변에 마커가 균등하게 분포한다.
     * 화면 중심 좌표가 누락되면 영역의 기하 중심을 자동 계산해 사용한다.
     */
    @Transactional(readOnly = true)
    public List<StoreListResponse> getStoresInBounds(
            double minLat, double maxLat, double minLng, double maxLng,
            Double centerLat, Double centerLng, int limit) {
        int limitedLimit = Math.min(limit, 100);
        double cLat = centerLat != null ? centerLat : (minLat + maxLat) / 2.0;
        double cLng = centerLng != null ? centerLng : (minLng + maxLng) / 2.0;
        return storeRepository
                .findInBounds(minLat, maxLat, minLng, maxLng, cLat, cLng, PageRequest.of(0, limitedLimit))
                .stream()
                .map(StoreListResponse::from)
                .toList();
    }

    /**
     * 매장 인기도 정렬 기준
     * 1. 평점 내림차순 → 2. 리뷰 수 내림차순 → 3. 북마크 수 내림차순
     * 4. (타이브레이커) id 오름차순 — 동률 시 먼저 등록된 매장이 위로 오도록 결정성 보장
     */
    private Sort popularitySort() {
        return Sort.by(
                Sort.Order.desc("averageStar"),
                Sort.Order.desc("reviewCount"),
                Sort.Order.desc("bookmarkCount"),
                Sort.Order.asc("id")
        );
    }

    /**
     * 이름(LIKE) + 카테고리/지역(EQ) + isDeleted=false 필터를 Specification으로 조립.
     */
    private Specification<Store> buildStoreSpec(String name, Category category, District district) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (name != null) {
                predicates.add(cb.like(cb.lower(root.get("storeName")),
                        "%" + name.toLowerCase() + "%"));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (district != null) {
                predicates.add(cb.equal(root.get("district"), district));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // 매장 상세조회 + 예약 가능 시간대 조회
    @Transactional(readOnly = true)
    public StoreDetailResponse getStore(Long storeId) {
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));

        List<RemainDateResponse> remainDates = storeRemainRepository
                .findDateAvailabilityByStoreId(storeId, LocalDate.now())
                .stream()
                .map(row -> new RemainDateResponse(
                        (LocalDate) row[0],
                        ((Long) row[1]) > 0
                ))
                .toList();

        return StoreDetailResponse.from(store, remainDates);
    }

    // 매장 정보 수정
    @CacheEvict(value = "storeList", allEntries = true)
    @Transactional
    public StoreUpdateResponse updateStore(Long userId, Long storeId, StoreUpdateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_STORE_UPDATE);
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.update(
                request.storeName(), request.storeImage(), request.category(),
                request.latitude(), request.longitude(), request.address(),
                request.district(), request.team(), request.openTime(), request.closeTime()
        );
        return StoreUpdateResponse.from(store);
    }

    // 매장 상태 변경
    @CacheEvict(value = "storeList", allEntries = true)
    @Transactional
    public StoreStatusUpdateResponse updateStoreStatus(Long userId, Long storeId, StoreStatusUpdateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_STORE_STATUS);
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.changeStatus(request.status());
        return StoreStatusUpdateResponse.from(store.getId(), store.getStatus().name());
    }

    /**
     * 자체 리뷰 생성 시 호출 — 외부 시드된 별점/리뷰수를 base로 평균에 합산.
     * 리스너에서 비동기로 호출되므로 리뷰 작성 자체엔 영향을 주지 않는다.
     */
    @CacheEvict(value = {"popularStores", "storeList"}, allEntries = true)
    @Transactional
    public void applyReviewCreated(Long storeId, int newStar) {
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.applyReviewCreated(newStar);
    }

    @CacheEvict(value = {"popularStores", "storeList"}, allEntries = true)
    @Transactional
    public void applyReviewDeleted(Long storeId, int deletedStar) {
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.applyReviewDeleted(deletedStar);
    }

    @CacheEvict(value = {"popularStores", "storeList"}, allEntries = true)
    @Transactional
    public void applyReviewUpdated(Long storeId, int oldStar, int newStar) {
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.applyReviewUpdated(oldStar, newStar);
    }
}
