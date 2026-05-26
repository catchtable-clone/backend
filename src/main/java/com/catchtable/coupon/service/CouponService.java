package com.catchtable.coupon.service;

import com.catchtable.coupon.dto.create.CouponTemplateCreateRequest;
import com.catchtable.coupon.dto.create.CouponTemplateCreateResponse;
import com.catchtable.coupon.dto.issue.CouponIssueResponse;
import com.catchtable.coupon.dto.read.CouponReadResponse;
import com.catchtable.coupon.dto.read.CouponTemplateActiveResponse;
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
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserRepository userRepository;
    private final RedisCouponIssuer redisCouponIssuer;

    // 쿠폰 템플릿 생성 + Redis 재고 워밍업
    @Transactional
    public CouponTemplateCreateResponse createTemplate(Long userId, CouponTemplateCreateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_COUPON_CREATE);
        CouponTemplate template = request.toEntity();
        CouponTemplate saved = couponTemplateRepository.save(template);

        // Lua가 참조할 재고 카운터와 발급자 SET을 만료 시각까지 유지.
        // 워밍업 없이는 첫 Lua 호출이 NOT_AVAILABLE 로 떨어진다.
        redisCouponIssuer.warmUp(saved.getId(), saved.getRemain(), saved.getExpiredAt());
        return CouponTemplateCreateResponse.from(saved);
    }

    /**
     * 선착순 발급.
     *
     * 1) Redis Lua: 재고/중복/차감/SADD 를 단일 EVAL 로 원자 결정.
     *    - @TimeLimiter + @CircuitBreaker 로 Redis 장애가 톰캣 워커를 점령하지 않게 격리.
     * 2) Lua 통과 = 발급 확정. 같은 트랜잭션 안에서 coupons row 1건 INSERT.
     * 3) DB INSERT 실패 시 Redis 상태 보상(stock INCR + issued SREM).
     */
    @Transactional
    public CouponIssueResponse issueCoupon(Long templateId, Long userId) {
        IssueResult result = unwrapIssueResult(templateId, userId);
        switch (result) {
            case DUPLICATE -> throw new CustomException(ErrorCode.DUPLICATE_COUPON);
            case EXHAUSTED -> throw new CustomException(ErrorCode.COUPON_EXHAUSTED);
            case NOT_AVAILABLE -> throw new CustomException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
            case UNAVAILABLE -> throw new CustomException(ErrorCode.COUPON_ISSUE_TEMPORARILY_UNAVAILABLE);
            case SUCCESS -> { /* fall through */ }
        }

        try {
            User user = userRepository.getById(userId);
            CouponTemplate template = couponTemplateRepository.findById(templateId)
                    .orElseThrow(() -> new CustomException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND));

            Coupon saved = couponRepository.save(
                    Coupon.builder()
                            .user(user)
                            .couponTemplate(template)
                            .build()
            );
            return CouponIssueResponse.from(saved);
        } catch (RuntimeException e) {
            // Redis 는 발급 확정 상태인데 DB INSERT 실패 → 보상.
            try {
                redisCouponIssuer.compensate(templateId, userId);
            } catch (Exception compEx) {
                log.error("쿠폰 발급 Redis 보상 실패. templateId={}, userId={}", templateId, userId, compEx);
            }
            throw e;
        }
    }

    /**
     * @TimeLimiter 가 요구하는 CompletableFuture 시그니처를 동기 값으로 풀어낸다.
     * 인프라 장애로 폴백된 경우 UNAVAILABLE 이 그대로 흘러나오고,
     * 비즈니스 예외(ignore-exceptions 통과)는 그대로 던져진다.
     */
    private IssueResult unwrapIssueResult(Long templateId, Long userId) {
        try {
            return redisCouponIssuer.tryIssue(templateId, userId).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CustomException ce) throw ce;
            if (cause instanceof RuntimeException re) throw re;
            throw new CustomException(ErrorCode.COUPON_ISSUE_TEMPORARILY_UNAVAILABLE);
        }
    }

    // 내 쿠폰 목록 조회
    @Transactional(readOnly = true)
    public List<CouponReadResponse> getMyCoupons(Long userId) {
        return couponRepository.findAllByUserId(userId).stream()
                .map(CouponReadResponse::from)
                .toList();
    }

    // 현재 발급 가능한 쿠폰 템플릿 목록 (홈 배너용) — 글로벌 데이터, 인증 불필요
    @Transactional(readOnly = true)
    public List<CouponTemplateActiveResponse> getActiveTemplates() {
        return couponTemplateRepository.findActiveTemplates(LocalDateTime.now()).stream()
                .map(CouponTemplateActiveResponse::from)
                .toList();
    }

    // 쿠폰 사용 (예약 생성 시 호출)
    @Transactional
    public Coupon useCoupon(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomException(ErrorCode.COUPON_NOT_FOUND));

        coupon.validateOwner(userId);
        coupon.use();

        return coupon;
    }

    // 쿠폰 반환 (예약 취소/변경 시 호출)
    @Transactional
    public void returnCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomException(ErrorCode.COUPON_NOT_FOUND));

        coupon.returnCoupon();
    }

    @Tool(description = "사용자가 예약 시 사용할 수 있는 쿠폰 목록을 조회합니다. " +
            "쿠폰 ID와 할인 정보를 포함하여 사용자에게 어떤 쿠폰을 사용할지 물어볼 때 사용됩니다.")
    @Transactional(readOnly = true)
    public String getAvailableCouponsForAi(ToolContext toolContext) {
        Long userId = (Long) toolContext.getContext().get("userId");
        log.info("AI Tool 호출: getAvailableCouponsForAi, userId={}", userId);

        List<Coupon> availableCoupons = couponRepository.findAllByUserId(userId).stream()
                .filter(coupon -> coupon.getStatus() == CouponStatus.UNUSED && coupon.getCouponTemplate().getExpiredAt().isAfter(LocalDateTime.now()))
                .collect(Collectors.toList());

        if (availableCoupons.isEmpty()) {
            return "사용 가능한 쿠폰이 없습니다.";
        }

        return availableCoupons.stream()
                .map(coupon -> {
                    String discountInfo;
                    if (coupon.getCouponTemplate().getDiscountRate() != null) {
                        discountInfo = coupon.getCouponTemplate().getDiscountRate() + "% 할인";
                    } else {
                        discountInfo = coupon.getCouponTemplate().getAmount() + "원 할인";
                    }
                    return String.format("%s (ID: %d, %s)",
                            coupon.getCouponTemplate().getCouponName(),
                            coupon.getId(),
                            discountInfo);
                })
                .collect(Collectors.joining(", "));
    }
}
