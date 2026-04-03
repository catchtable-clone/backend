package com.catchtable.store.service;

import com.catchtable.store.dto.StoreCreateRequest;
import com.catchtable.store.dto.StoreCreateResponse;
import com.catchtable.store.dto.StoreDetailResponse;
import com.catchtable.store.dto.StoreListResponse;
import com.catchtable.store.entity.*;
import com.catchtable.store.repository.StoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private StoreService storeService;

    private Store createTestStore(Long id, String name, Boolean isDeleted) {
        return Store.builder()
                .id(id)
                .storeName(name)
                .storeImage(null)
                .category(Category.WESTERN)
                .latitude(37.5340)
                .longitude(126.9930)
                .address("서울 용산구 이태원로 246")
                .district(District.YONGSAN)
                .team(10)
                .openTime("11:00")
                .closeTime("22:00")
                .isDeleted(isDeleted)
                .build();
    }

    @Test
    @DisplayName("매장 등록 성공")
    void createStore() {
        // given
        Store saved = createTestStore(1L, "모수 서울", false);
        given(storeRepository.save(any(Store.class))).willReturn(saved);

        StoreCreateRequest request = new StoreCreateRequest();
        setField(request, "storeName", "모수 서울");
        setField(request, "category", Category.WESTERN);
        setField(request, "latitude", 37.5340);
        setField(request, "longitude", 126.9930);
        setField(request, "address", "서울 용산구 이태원로 246");
        setField(request, "district", District.YONGSAN);
        setField(request, "team", 10);
        setField(request, "openTime", "11:00");
        setField(request, "closeTime", "22:00");

        // when
        StoreCreateResponse response = storeService.createStore(request);

        // then
        assertThat(response.getStoreId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("매장명 검색 성공")
    void searchStores() {
        // given
        List<Store> stores = List.of(
                createTestStore(1L, "모수 서울", false),
                createTestStore(2L, "모수 부산", false)
        );
        given(storeRepository.searchByStoreName("모수")).willReturn(stores);

        // when
        List<StoreListResponse> result = storeService.searchStores("모수");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStoreName()).isEqualTo("모수 서울");
        assertThat(result.get(1).getStoreName()).isEqualTo("모수 부산");
    }

    @Test
    @DisplayName("매장명 검색 결과 없음 - 빈 리스트 반환")
    void searchStoresEmpty() {
        // given
        given(storeRepository.searchByStoreName("없는매장")).willReturn(List.of());

        // when
        List<StoreListResponse> result = storeService.searchStores("없는매장");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("매장 상세 조회 성공")
    void getStore() {
        // given
        Store store = createTestStore(1L, "모수 서울", false);
        given(storeRepository.findById(1L)).willReturn(Optional.of(store));

        // when
        StoreDetailResponse result = storeService.getStore(1L);

        // then
        assertThat(result.getStoreId()).isEqualTo(1L);
        assertThat(result.getStoreName()).isEqualTo("모수 서울");
        assertThat(result.getCategory()).isEqualTo("WESTERN");
        assertThat(result.getDistrict()).isEqualTo("YONGSAN");
        assertThat(result.getTeam()).isEqualTo(10);
    }

    @Test
    @DisplayName("존재하지 않는 매장 조회 시 예외 발생")
    void getStoreNotFound() {
        // given
        given(storeRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeService.getStore(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매장을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("삭제된 매장 조회 시 예외 발생")
    void getDeletedStore() {
        // given
        Store deletedStore = createTestStore(1L, "삭제된 매장", true);
        given(storeRepository.findById(1L)).willReturn(Optional.of(deletedStore));

        // when & then
        assertThatThrownBy(() -> storeService.getStore(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매장을 찾을 수 없습니다.");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
