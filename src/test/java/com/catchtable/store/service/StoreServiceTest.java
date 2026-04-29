package com.catchtable.store.service;

import com.catchtable.store.dto.status.StoreStatusUpdateRequest;
import com.catchtable.store.dto.status.StoreStatusUpdateResponse;
import com.catchtable.store.dto.create.StoreCreateRequest;
import com.catchtable.store.entity.*;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.repository.UserRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StoreRemainRepository storeRemainRepository;

    @InjectMocks
    private StoreService storeService;

    private Store createTestStore(Long id, String name, StoreStatus status, Boolean isDeleted) {
        return Store.builder()
                .id(id)
                .storeName(name)
                .category(Category.WESTERN)
                .latitude(37.5340)
                .longitude(126.9930)
                .address("서울 용산구 이태원로 246")
                .district(District.YONGSAN)
                .team(10)
                .openTime("11:00")
                .closeTime("22:00")
                .status(status)
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

    // === 권한 검증 ===

    @Test
    @DisplayName("매장 등록 실패 - 일반 사용자 권한 없음")
    void createStoreFailNotAdmin() {
        given(userRepository.getAdminOrThrow(eq(2L), any(ErrorCode.class)))
                .willThrow(new CustomException(ErrorCode.ADMIN_ONLY_STORE_CREATE));

        var request = new StoreCreateRequest("모수 서울", null, Category.WESTERN,
                37.534, 126.993, "서울 용산구", District.YONGSAN, 10, "11:00", "22:00");

        assertThatThrownBy(() -> storeService.createStore(2L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ADMIN_ONLY_STORE_CREATE));
    }

    // === 매장 상태 변경 ===

    @Test
    @DisplayName("매장 상태 변경 성공 - ACTIVE → REST")
    void updateStoreStatusToRest() {
        given(userRepository.getAdminOrThrow(eq(1L), any(ErrorCode.class))).willReturn(createAdminUser());
        Store store = createTestStore(1L, "모수 서울", StoreStatus.ACTIVE, false);
        given(storeRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(store));

        var request = new StoreStatusUpdateRequest(StoreStatus.REST);

        StoreStatusUpdateResponse response = storeService.updateStoreStatus(1L, 1L, request);

        assertThat(response.status()).isEqualTo("REST");
    }

    @Test
    @DisplayName("매장 상태 변경 성공 - ACTIVE → INACTIVE (soft delete)")
    void updateStoreStatusToInactive() {
        given(userRepository.getAdminOrThrow(eq(1L), any(ErrorCode.class))).willReturn(createAdminUser());
        Store store = createTestStore(1L, "모수 서울", StoreStatus.ACTIVE, false);
        given(storeRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(store));

        var request = new StoreStatusUpdateRequest(StoreStatus.INACTIVE);

        StoreStatusUpdateResponse response = storeService.updateStoreStatus(1L, 1L, request);

        assertThat(response.status()).isEqualTo("INACTIVE");
        assertThat(store.getIsDeleted()).isTrue();
    }

    @Test
    @DisplayName("매장 상태 변경 실패 - INACTIVE 상태에서 변경 불가")
    void updateStoreStatusFailFromInactive() {
        given(userRepository.getAdminOrThrow(eq(1L), any(ErrorCode.class))).willReturn(createAdminUser());
        Store store = createTestStore(1L, "모수 서울", StoreStatus.INACTIVE, true);
        given(storeRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(store));

        var request = new StoreStatusUpdateRequest(StoreStatus.ACTIVE);

        assertThatThrownBy(() -> storeService.updateStoreStatus(1L, 1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INACTIVE_STORE));
    }

    @Test
    @DisplayName("매장 상태 변경 실패 - 동일 상태로 변경")
    void updateStoreStatusFailSameStatus() {
        given(userRepository.getAdminOrThrow(eq(1L), any(ErrorCode.class))).willReturn(createAdminUser());
        Store store = createTestStore(1L, "모수 서울", StoreStatus.ACTIVE, false);
        given(storeRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(store));

        var request = new StoreStatusUpdateRequest(StoreStatus.ACTIVE);

        assertThatThrownBy(() -> storeService.updateStoreStatus(1L, 1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SAME_STATUS));
    }
}
