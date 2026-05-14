package com.catchtable.coupon.service;

import com.catchtable.coupon.dto.create.CouponTemplateCreateRequest;
import com.catchtable.coupon.dto.create.CouponTemplateCreateResponse;
import com.catchtable.coupon.dto.issue.CouponIssueResponse;
import com.catchtable.coupon.dto.read.CouponReadResponse;
import com.catchtable.coupon.dto.read.CouponTemplateActiveResponse;
import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponStatus;
import com.catchtable.coupon.entity.CouponTemplate;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserRepository userRepository;

    // 쿠폰 템플릿 생성
    @Transactional
    public CouponTemplateCreateResponse createTemplate(Long userId, CouponTemplateCreateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_COUPON_CREATE);
        CouponTemplate template = request.toEntity();
        CouponTemplate saved = couponTemplateRepository.save(template);
        return CouponTemplateCreateResponse.from(saved);
    }

    // 쿠폰 발급 (비관적 락)
    @Transactional
    public CouponIssueResponse issueCoupon(Long templateId, Long userId) {
        User user = userRepository.getById(userId);

        CouponTemplate template = couponTemplateRepository.findByIdWithLock(templateId)
                .orElseThrow(() -> new CustomException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND));

        if (couponRepository.existsByUserIdAndCouponTemplateId(userId, templateId)) {
            throw new CustomException(ErrorCode.DUPLICATE_COUPON);
        }

        template.decreaseRemain();

        Coupon coupon = Coupon.builder()
                .user(user)
                .couponTemplate(template)
                .build();

        Coupon saved = couponRepository.save(coupon);
        return CouponIssueResponse.from(saved);
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
