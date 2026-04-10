package com.catchtable.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCode {

    // Store
    STORE_CREATED(201, "매장이 등록되었습니다."),
    STORE_LIST_OK(200, "매장 목록을 조회했습니다."),
    STORE_DETAIL_OK(200, "매장 정보를 조회했습니다."),
    STORE_UPDATED(200, "매장 정보가 수정되었습니다."),
    STORE_STATUS_UPDATED(200, "매장 상태가 변경되었습니다."),

    // Coupon
    COUPON_ISSUED(200, "발급되었습니다."),
    COUPON_LIST_OK(200, "내 쿠폰 목록을 조회했습니다."),

    // Error - 400
    BAD_REQUEST(400, "잘못된 요청입니다."),
    DUPLICATE_COUPON(400, "이미 발급받은 쿠폰입니다."),
    SAME_STATUS(400, "현재 상태와 동일한 상태로 변경할 수 없습니다."),
    INACTIVE_STORE(400, "비활성화된 매장의 상태는 변경할 수 없습니다."),
    OWN_COUPON_ONLY(400, "본인의 쿠폰만 사용할 수 있습니다."),

    // Error - 403
    FORBIDDEN(403, "접근 권한이 없습니다."),
    ADMIN_ONLY_STORE_CREATE(403, "관리자만 매장을 등록할 수 있습니다."),
    ADMIN_ONLY_STORE_UPDATE(403, "관리자만 매장을 수정할 수 있습니다."),
    ADMIN_ONLY_STORE_STATUS(403, "관리자만 매장 상태를 변경할 수 있습니다."),

    // Error - 404
    NOT_FOUND(404, "리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    STORE_NOT_FOUND(404, "매장을 찾을 수 없습니다."),
    COUPON_NOT_FOUND(404, "쿠폰을 찾을 수 없습니다."),
    COUPON_TEMPLATE_NOT_FOUND(404, "쿠폰 템플릿을 찾을 수 없습니다."),

    // Error - 500
    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String message;
}
