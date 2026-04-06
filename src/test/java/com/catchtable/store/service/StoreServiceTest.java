package com.catchtable.store.service;

import com.catchtable.store.dto.create.StoreCreateRequest;
import com.catchtable.store.dto.create.StoreCreateResponse;
import com.catchtable.store.dto.read.StoreDetailResponse;
import com.catchtable.store.dto.read.StoreListResponse;
import com.catchtable.store.dto.status.StoreStatusUpdateRequest;
import com.catchtable.store.dto.status.StoreStatusUpdateResponse;
import com.catchtable.store.dto.update.StoreUpdateRequest;
import com.catchtable.store.dto.update.StoreUpdateResponse;
import com.catchtable.store.entity.*;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.repository.UserRepository;
import com.catchtable.global.exception.AccessDeniedException;
import com.catchtable.global.exception.ResourceNotFoundException;
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

    @Mock
    private UserRepository userRepository;

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

    private User createAdminUser() {
        return User.builder()
                .id(1L)
                .email("admin@gmail.com")
                .nickname("관리자")
                .googleId("google-admin-123")
                .role(UserRole.ADMIN)
                .build();
    }

    private User createNormalUser() {
        return User.builder()
                .id(2L)
                .email("user@gmail.com")
                .nickname("일반유저")
                .googleId("google-user-123")
                .role(UserRole.USER)
                .build();
    }

    @Test
    @DisplayName("매장 등록 성공 - 관리자")
    void createStore() {
        // given
        User admin = createAdminUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

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
        StoreCreateResponse response = storeService.createStore(1L, request);

        // then
        assertThat(response.getStoreId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("매장 등록 실패 - 일반 사용자")
    void createStoreFailNotAdmin() {
        // given
        User normalUser = createNormalUser();
        given(userRepository.findById(2L)).willReturn(Optional.of(normalUser));

        StoreCreateRequest request = new StoreCreateRequest();

        // when & then
        assertThatThrownBy(() -> storeService.createStore(2L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("관리자만 매장을 등록할 수 있습니다.");
    }

    @Test
    @DisplayName("매장 수정 성공 - 관리자")
    void updateStore() {
        // given
        User admin = createAdminUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        Store store = createTestStore(1L, "모수 서울", false);
        given(storeRepository.findById(1L)).willReturn(Optional.of(store));

        StoreUpdateRequest request = new StoreUpdateRequest();
        setField(request, "storeName", "모수 부산");
        setField(request, "category", Category.WESTERN);
        setField(request, "latitude", 35.1796);
        setField(request, "longitude", 129.0756);
        setField(request, "address", "부산 해운대구");
        setField(request, "district", District.YONGSAN);
        setField(request, "team", 8);
        setField(request, "openTime", "10:00");
        setField(request, "closeTime", "21:00");

        // when
        StoreUpdateResponse response = storeService.updateStore(1L, 1L, request);

        // then
        assertThat(response.getStoreName()).isEqualTo("모수 부산");
        assertThat(response.getAddress()).isEqualTo("부산 해운대구");
        assertThat(response.getTeam()).isEqualTo(8);
    }

    @Test
    @DisplayName("매장 수정 실패 - 존재하지 않는 매장")
    void updateStoreNotFound() {
        // given
        User admin = createAdminUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));
        given(storeRepository.findById(999L)).willReturn(Optional.empty());

        StoreUpdateRequest request = new StoreUpdateRequest();

        // when & then
        assertThatThrownBy(() -> storeService.updateStore(1L, 999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("매장을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("매장 상태 변경 성공 - ACTIVE → REST")
    void updateStoreStatusToRest() {
        // given
        User admin = createAdminUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        Store store = createTestStore(1L, "모수 서울", false);
        given(storeRepository.findById(1L)).willReturn(Optional.of(store));

        StoreStatusUpdateRequest request = new StoreStatusUpdateRequest();
        setField(request, "status", StoreStatus.REST);

        // when
        StoreStatusUpdateResponse response = storeService.updateStoreStatus(1L, 1L, request);

        // then
        assertThat(response.getStoreId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("REST");
    }

    @Test
    @DisplayName("매장 상태 변경 성공 - ACTIVE → INACTIVE (soft delete)")
    void updateStoreStatusToInactive() {
        // given
        User admin = createAdminUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        Store store = createTestStore(1L, "모수 서울", false);
        given(storeRepository.findById(1L)).willReturn(Optional.of(store));

        StoreStatusUpdateRequest request = new StoreStatusUpdateRequest();
        setField(request, "status", StoreStatus.INACTIVE);

        // when
        StoreStatusUpdateResponse response = storeService.updateStoreStatus(1L, 1L, request);

        // then
        assertThat(response.getStoreId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        assertThat(store.getIsDeleted()).isTrue();
    }

    @Test
    @DisplayName("매장 상태 변경 실패 - INACTIVE 상태에서 변경 시도")
    void updateStoreStatusFailFromInactive() {
        // given
        User admin = createAdminUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        Store store = Store.builder()
                .id(1L)
                .storeName("모수 서울")
                .category(Category.WESTERN)
                .latitude(37.5340)
                .longitude(126.9930)
                .address("서울 용산구 이태원로 246")
                .district(District.YONGSAN)
                .team(10)
                .openTime("11:00")
                .closeTime("22:00")
                .status(StoreStatus.INACTIVE)
                .isDeleted(true)
                .build();
        given(storeRepository.findById(1L)).willReturn(Optional.of(store));

        StoreStatusUpdateRequest request = new StoreStatusUpdateRequest();
        setField(request, "status", StoreStatus.ACTIVE);

        // when & then
        assertThatThrownBy(() -> storeService.updateStoreStatus(1L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비활성화된 매장의 상태는 변경할 수 없습니다.");
    }

    @Test
    @DisplayName("매장 상태 변경 실패 - 동일 상태로 변경")
    void updateStoreStatusFailSameStatus() {
        // given
        User admin = createAdminUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        Store store = createTestStore(1L, "모수 서울", false);
        given(storeRepository.findById(1L)).willReturn(Optional.of(store));

        StoreStatusUpdateRequest request = new StoreStatusUpdateRequest();
        setField(request, "status", StoreStatus.ACTIVE);

        // when & then
        assertThatThrownBy(() -> storeService.updateStoreStatus(1L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 상태와 동일한 상태로 변경할 수 없습니다.");
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
                .isInstanceOf(ResourceNotFoundException.class)
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
                .isInstanceOf(ResourceNotFoundException.class)
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
