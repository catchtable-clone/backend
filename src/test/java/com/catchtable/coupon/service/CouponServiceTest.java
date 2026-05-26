package com.catchtable.coupon.service;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponStatus;
import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.redis.RedisCouponIssuer;
import com.catchtable.coupon.redis.RedisCouponIssuer.IssueResult;
import com.catchtable.coupon.repository.CouponRepository;
import com.catchtable.coupon.repository.CouponTemplateRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisCouponIssuer redisCouponIssuer;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private CouponService couponService;

    @BeforeEach
    void stubTransactionTemplate() {
        // CouponService 가 TransactionTemplate.execute() 로 짧은 트랜잭션을 묶기 때문에
        // 단위 테스트에서는 콜백을 즉시 실행하도록 stub 한다.
        // lenient — 트랜잭션을 안 쓰는 테스트(쿠폰 사용/반환/조회 등)에서 미사용 경고 방지.
        Mockito.lenient().when(transactionTemplate.execute(Mockito.any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
    }

    private User createUser(Long id) {
        return User.builder()
                .id(id)
                .email("user@gmail.com")
                .nickname("유저")
                .googleId("google-123")
                .role(UserRole.USER)
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

    // === 쿠폰 템플릿 생성 ===

    @Test
    @DisplayName("쿠폰 템플릿 생성 성공 - 관리자")
    void createTemplateSuccess() {
        given(userRepository.getAdminOrThrow(1L, ErrorCode.ADMIN_ONLY_COUPON_CREATE))
                .willReturn(createAdminUser());
        given(couponTemplateRepository.save(any(CouponTemplate.class))).willAnswer(invocation -> {
            CouponTemplate t = invocation.getArgument(0);
            setField(t, "id", 1L);
            return t;
        });

        var request = new com.catchtable.coupon.dto.create.CouponTemplateCreateRequest(
                "10% 할인", 10, 100, LocalDateTime.now(), LocalDateTime.now().plusDays(30));

        var response = couponService.createTemplate(1L, request);

        assertThat(response.templateId()).isEqualTo(1L);
        assertThat(response.couponName()).isEqualTo("10% 할인");
    }

    @Test
    @DisplayName("쿠폰 템플릿 생성 실패 - 일반 사용자 권한 없음")
    void createTemplateFailNotAdmin() {
        given(userRepository.getAdminOrThrow(2L, ErrorCode.ADMIN_ONLY_COUPON_CREATE))
                .willThrow(new CustomException(ErrorCode.ADMIN_ONLY_COUPON_CREATE));

        var request = new com.catchtable.coupon.dto.create.CouponTemplateCreateRequest(
                "10% 할인", 10, 100, LocalDateTime.now(), LocalDateTime.now().plusDays(30));

        assertThatThrownBy(() -> couponService.createTemplate(2L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ADMIN_ONLY_COUPON_CREATE));
    }

    // === 쿠폰 발급 ===

    @Test
    @DisplayName("쿠폰 발급 성공 - Redis Lua 통과 후 coupons row INSERT")
    void issueCouponSuccess() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));

        given(redisCouponIssuer.tryIssue(1L, 1L)).willReturn(IssueResult.SUCCESS);
        given(userRepository.getById(1L)).willReturn(user);
        given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(template));
        given(couponRepository.save(any(Coupon.class))).willAnswer(invocation -> {
            Coupon coupon = invocation.getArgument(0);
            setField(coupon, "id", 1L);
            return coupon;
        });

        var response = couponService.issueCoupon(1L, 1L);

        assertThat(response.couponId()).isEqualTo(1L);
        assertThat(response.couponName()).isEqualTo("10% 할인");
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - Redis 장애로 회로 폴백 시 503 매핑")
    void issueCouponFailRedisUnavailable() {
        given(redisCouponIssuer.tryIssue(1L, 1L)).willReturn(IssueResult.UNAVAILABLE);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COUPON_ISSUE_TEMPORARILY_UNAVAILABLE));
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 중복 발급 (Redis Lua 가 DUPLICATE 반환)")
    void issueCouponFailDuplicate() {
        given(redisCouponIssuer.tryIssue(1L, 1L)).willReturn(IssueResult.DUPLICATE);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_COUPON));
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 수량 소진 (Redis Lua 가 EXHAUSTED 반환)")
    void issueCouponFailExhausted() {
        given(redisCouponIssuer.tryIssue(1L, 1L)).willReturn(IssueResult.EXHAUSTED);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COUPON_EXHAUSTED));
    }

    @Test
    @DisplayName("쿠폰 발급 - DB INSERT 실패 시 Redis 보상 호출")
    void issueCouponDbFailureCompensatesRedis() {
        User user = createUser(1L);
        CouponTemplate template = createTemplate(10, LocalDateTime.now().plusDays(30));

        given(redisCouponIssuer.tryIssue(1L, 1L)).willReturn(IssueResult.SUCCESS);
        given(userRepository.getById(1L)).willReturn(user);
        given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(template));
        given(couponRepository.save(any(Coupon.class)))
                .willThrow(new RuntimeException("DB INSERT 실패 시뮬레이션"));

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(RuntimeException.class);

        verify(redisCouponIssuer).compensate(1L, 1L);
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
