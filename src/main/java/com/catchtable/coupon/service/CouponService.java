package com.catchtable.coupon.service;

import com.catchtable.coupon.dto.create.CouponTemplateCreateRequest;
import com.catchtable.coupon.dto.create.CouponTemplateCreateResponse;
import com.catchtable.coupon.dto.issue.CouponIssueResponse;
import com.catchtable.coupon.dto.read.CouponReadResponse;
import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.repository.CouponRepository;
import com.catchtable.coupon.repository.CouponTemplateRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserRepository userRepository;

    // 쿠폰 템플릿 생성
    @Transactional
    public CouponTemplateCreateResponse createTemplate(Long userId, CouponTemplateCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_ONLY_COUPON_CREATE);
        }
        CouponTemplate template = request.toEntity();
        CouponTemplate saved = couponTemplateRepository.save(template);
        return CouponTemplateCreateResponse.from(saved);
    }

    // 쿠폰 발급 (비관적 락)
    @Transactional
    public CouponIssueResponse issueCoupon(Long templateId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

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

    // 쿠폰 사용 (예약 생성 시 호출)
    @Transactional
    public void useCoupon(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomException(ErrorCode.COUPON_NOT_FOUND));

        if (!coupon.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.OWN_COUPON_ONLY);
        }

        coupon.use();
    }

    // 쿠폰 반환 (예약 취소/변경 시 호출)
    @Transactional
    public void returnCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomException(ErrorCode.COUPON_NOT_FOUND));

        coupon.returnCoupon();
    }
}
