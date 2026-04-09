package com.catchtable.coupon.service;

import com.catchtable.coupon.dto.issue.CouponIssueResponse;
import com.catchtable.coupon.dto.read.CouponReadResponse;
import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.repository.CouponRepository;
import com.catchtable.coupon.repository.CouponTemplateRepository;
import com.catchtable.global.exception.ResourceNotFoundException;
import com.catchtable.user.entity.User;
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

    @Transactional
    public CouponIssueResponse issueCoupon(Long templateId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));

        CouponTemplate template = couponTemplateRepository.findByIdWithLock(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("쿠폰 템플릿을 찾을 수 없습니다."));

        if (couponRepository.existsByUserIdAndCouponTemplateId(userId, templateId)) {
            throw new IllegalArgumentException("이미 발급받은 쿠폰입니다.");
        }

        template.decreaseRemain();

        Coupon coupon = Coupon.builder()
                .user(user)
                .couponTemplate(template)
                .build();

        Coupon saved = couponRepository.save(coupon);
        return CouponIssueResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CouponReadResponse> getMyCoupons(Long userId) {
        return couponRepository.findAllByUserId(userId).stream()
                .map(CouponReadResponse::from)
                .toList();
    }

    @Transactional
    public void useCoupon(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("쿠폰을 찾을 수 없습니다."));

        if (!coupon.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인의 쿠폰만 사용할 수 있습니다.");
        }

        coupon.use();
    }

    @Transactional
    public void returnCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("쿠폰을 찾을 수 없습니다."));

        coupon.returnCoupon();
    }
}
