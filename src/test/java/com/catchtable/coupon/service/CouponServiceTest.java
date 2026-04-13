package com.catchtable.coupon.service;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponStatus;
import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.repository.CouponRepository;
import com.catchtable.coupon.repository.CouponTemplateRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CouponService couponService;

    private User createUser(Long id) {
        return User.builder()
                .id(id)
                .email("user@gmail.com")
                .nickname("유저")
                .googleId("google-123")
                .role(UserRole.USER)
                .build();
    }

    private CouponTemplate createTemplate(Integer remain, LocalDateTime expiredAt) {
        return CouponTemplate.builder()
                .id(1L)
                .couponName("10% 할인")
                .discountRate(10)
                .amount(100)
                .remain(remain)
                .startedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(expiredAt)
                .build();
    }

    private Coupon createCoupon(Long id, User user, CouponTemplate template, CouponStatus status) {
        return Coupon.builder()
                .id(id)
                .user(user)
                .couponTemplate(template)
                .status(status)
                .build();
    }

    // === 쿠폰 발급 ===

    @Test
    @DisplayName("쿠폰 발급 성공 - 수량 차감 확인")
    void issueCouponSuccess() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(couponTemplateRepository.findByIdWithLock(1L)).willReturn(Optional.of(template));
        given(couponRepository.existsByUserIdAndCouponTemplateId(1L, 1L)).willReturn(false);
        given(couponRepository.save(any(Coupon.class))).willAnswer(invocation -> {
            Coupon coupon = invocation.getArgument(0);
            setField(coupon, "id", 1L);
            return coupon;
        });

        couponService.issueCoupon(1L, 1L);

        assertThat(template.getRemain()).isEqualTo(9);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 중복 발급")
    void issueCouponFailDuplicate() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(couponTemplateRepository.findByIdWithLock(1L)).willReturn(Optional.of(template));
        given(couponRepository.existsByUserIdAndCouponTemplateId(1L, 1L)).willReturn(true);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_COUPON));
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 수량 소진")
    void issueCouponFailExhausted() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(0, LocalDateTime.now().plusDays(30));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(couponTemplateRepository.findByIdWithLock(1L)).willReturn(Optional.of(template));
        given(couponRepository.existsByUserIdAndCouponTemplateId(1L, 1L)).willReturn(false);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COUPON_EXHAUSTED));
    }

    // === 쿠폰 사용 ===

    @Test
    @DisplayName("쿠폰 사용 성공")
    void useCouponSuccess() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));
        Coupon coupon = createCoupon(1L, user, template, CouponStatus.UNUSED);

        given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

        couponService.useCoupon(1L, 1L);

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("쿠폰 사용 실패 - 만료된 쿠폰")
    void useCouponFailExpired() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().minusDays(1));
        Coupon coupon = createCoupon(1L, user, template, CouponStatus.UNUSED);

        given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.useCoupon(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COUPON_EXPIRED));
    }

    @Test
    @DisplayName("쿠폰 사용 실패 - 이미 사용된 쿠폰")
    void useCouponFailAlreadyUsed() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));
        Coupon coupon = createCoupon(1L, user, template, CouponStatus.USED);

        given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.useCoupon(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COUPON_NOT_USABLE));
    }

    @Test
    @DisplayName("쿠폰 사용 실패 - 본인 쿠폰 아님")
    void useCouponFailNotOwner() {
        User owner = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));
        Coupon coupon = createCoupon(1L, owner, template, CouponStatus.UNUSED);

        given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.useCoupon(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.OWN_COUPON_ONLY));
    }

    // === 쿠폰 반환 ===

    @Test
    @DisplayName("쿠폰 반환 성공")
    void returnCouponSuccess() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));
        Coupon coupon = createCoupon(1L, user, template, CouponStatus.USED);

        given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

        couponService.returnCoupon(1L);

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.UNUSED);
    }

    @Test
    @DisplayName("쿠폰 반환 실패 - 미사용 쿠폰")
    void returnCouponFailNotUsed() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));
        Coupon coupon = createCoupon(1L, user, template, CouponStatus.UNUSED);

        given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.returnCoupon(1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COUPON_NOT_RETURNABLE));
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
