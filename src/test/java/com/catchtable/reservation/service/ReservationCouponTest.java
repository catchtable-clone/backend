package com.catchtable.reservation.service;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponStatus;
import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.repository.CouponRepository;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.reservation.repository.ReservationRepository;
import com.catchtable.store.entity.*;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReservationCouponTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRemainRepository storeRemainRepository;
    @Mock
    private CouponService couponService;
    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private ReservationService reservationService;

    private User user;
    private Store store;
    private StoreRemain storeRemain;
    private CouponTemplate template;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("user@gmail.com")
                .nickname("유저")
                .googleId("google-123")
                .role(UserRole.USER)
                .build();

        store = Store.builder()
                .id(1L)
                .storeName("모수 서울")
                .category(Category.WESTERN)
                .latitude(37.534)
                .longitude(126.993)
                .address("서울 용산구")
                .district(District.YONGSAN)
                .team(10)
                .openTime("11:00")
                .closeTime("22:00")
                .build();

        storeRemain = StoreRemain.builder()
                .store(store)
                .remainDate(LocalDate.now().plusDays(1))
                .remainTime(LocalTime.of(12, 0))
                .remainTeam(5)
                .build();
        setField(storeRemain, "id", 1L);

        template = CouponTemplate.builder()
                .id(1L)
                .couponName("10% 할인")
                .discountRate(10)
                .amount(100)
                .remain(50)
                .startedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        coupon = Coupon.builder()
                .id(1L)
                .user(user)
                .couponTemplate(template)
                .status(CouponStatus.UNUSED)
                .build();
    }

    // === 예약 생성 시 쿠폰 적용 ===

    @Test
    @DisplayName("예약 생성 시 쿠폰 적용 성공")
    void createReservationWithCoupon() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(storeRemainRepository.findByIdWithStore(1L)).willReturn(Optional.of(storeRemain));
        given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            setField(r, "id", 1L);
            return r;
        });

        ReservationCreateRequestDto request = new ReservationCreateRequestDto(1L, 1L, 4, 1L);
        ReservationCreateResponseDto response = reservationService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        verify(couponService).useCoupon(1L, 1L);
    }

    @Test
    @DisplayName("예약 생성 시 쿠폰 없이 성공")
    void createReservationWithoutCoupon() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(storeRemainRepository.findByIdWithStore(1L)).willReturn(Optional.of(storeRemain));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            setField(r, "id", 1L);
            return r;
        });

        ReservationCreateRequestDto request = new ReservationCreateRequestDto(1L, 1L, 4, null);
        ReservationCreateResponseDto response = reservationService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        verify(couponService, never()).useCoupon(any(), any());
    }

    // === 예약 취소 시 쿠폰 반환 ===

    @Test
    @DisplayName("예약 취소 시 쿠폰 반환 성공")
    void cancelReservationReturnsCoupon() {
        Reservation reservation = Reservation.builder()
                .user(user)
                .storeRemain(storeRemain)
                .coupon(coupon)
                .member(4)
                .status(ReservationStatus.PENDING)
                .build();
        setField(reservation, "id", 1L);

        given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));

        reservationService.cancelReservation(1L, 1L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        verify(couponService).returnCoupon(1L);
    }

    @Test
    @DisplayName("예약 취소 시 쿠폰 없으면 반환 호출 안 함")
    void cancelReservationWithoutCoupon() {
        Reservation reservation = Reservation.builder()
                .user(user)
                .storeRemain(storeRemain)
                .coupon(null)
                .member(4)
                .status(ReservationStatus.PENDING)
                .build();
        setField(reservation, "id", 1L);

        given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));

        reservationService.cancelReservation(1L, 1L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        verify(couponService, never()).returnCoupon(any());
    }

    // === 예약 변경 시 기존 쿠폰 반환 ===

    @Test
    @DisplayName("예약 변경 시 기존 쿠폰 반환 성공")
    void updateReservationReturnsCoupon() {
        Reservation oldReservation = Reservation.builder()
                .user(user)
                .storeRemain(storeRemain)
                .coupon(coupon)
                .member(4)
                .status(ReservationStatus.PENDING)
                .build();
        setField(oldReservation, "id", 1L);

        StoreRemain newStoreRemain = StoreRemain.builder()
                .store(store)
                .remainDate(LocalDate.now().plusDays(1))
                .remainTime(LocalTime.of(13, 0))
                .remainTeam(3)
                .build();
        setField(newStoreRemain, "id", 2L);

        given(reservationRepository.findById(1L)).willReturn(Optional.of(oldReservation));
        given(storeRemainRepository.findByIdWithStore(2L)).willReturn(Optional.of(newStoreRemain));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            setField(r, "id", 2L);
            return r;
        });

        var request = new com.catchtable.reservation.dto.update.ReservationUpdateRequestDto(2L, 2);
        reservationService.updateReservation(1L, 1L, request);

        assertThat(oldReservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        verify(couponService).returnCoupon(1L);
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
