package com.catchtable.global.exception;

import com.catchtable.global.common.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode implements ResponseCode {

    // Common
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // Auth
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 구글 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다."),

    // Store
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매장입니다."),
    ADMIN_ONLY_STORE_CREATE(HttpStatus.FORBIDDEN, "관리자만 매장을 등록할 수 있습니다."),
    ADMIN_ONLY_STORE_UPDATE(HttpStatus.FORBIDDEN, "관리자만 매장을 수정할 수 있습니다."),
    ADMIN_ONLY_STORE_STATUS(HttpStatus.FORBIDDEN, "관리자만 매장 상태를 변경할 수 있습니다."),
    INACTIVE_STORE(HttpStatus.BAD_REQUEST, "비활성화된 매장의 상태는 변경할 수 없습니다."),
    SAME_STATUS(HttpStatus.BAD_REQUEST, "현재 상태와 동일한 상태로 변경할 수 없습니다."),

    // Coupon
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 쿠폰입니다."),
    COUPON_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."),
    ADMIN_ONLY_COUPON_CREATE(HttpStatus.FORBIDDEN, "관리자만 쿠폰을 생성할 수 있습니다."),
    DUPLICATE_COUPON(HttpStatus.BAD_REQUEST, "이미 발급받은 쿠폰입니다."),
    OWN_COUPON_ONLY(HttpStatus.BAD_REQUEST, "본인의 쿠폰만 사용할 수 있습니다."),
    COUPON_NOT_USABLE(HttpStatus.BAD_REQUEST, "사용 가능한 쿠폰이 아닙니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 쿠폰입니다."),
    COUPON_NOT_RETURNABLE(HttpStatus.BAD_REQUEST, "사용된 쿠폰만 반환할 수 있습니다."),
    COUPON_EXHAUSTED(HttpStatus.BAD_REQUEST, "쿠폰이 모두 소진되었습니다."),

    // Menu
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 메뉴입니다."),
    ADMIN_ONLY_MENU_CREATE(HttpStatus.FORBIDDEN, "관리자만 메뉴를 등록할 수 있습니다."),
    ADMIN_ONLY_MENU_UPDATE(HttpStatus.FORBIDDEN, "관리자만 메뉴를 수정할 수 있습니다."),
    ADMIN_ONLY_MENU_DELETE(HttpStatus.FORBIDDEN, "관리자만 메뉴를 삭제할 수 있습니다."),
    MENU_STORE_MISMATCH(HttpStatus.FORBIDDEN, "해당 매장의 메뉴가 아닙니다."),

    // Bookmark
    BOOKMARK_FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 폴더입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 즐겨찾기입니다."),
    BOOKMARK_FOLDER_NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 폴더만 접근할 수 있습니다."),
    BOOKMARK_NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 즐겨찾기만 삭제할 수 있습니다."),
    BOOKMARK_DUPLICATE(HttpStatus.BAD_REQUEST, "이미 해당 폴더에 저장된 매장입니다."),
    BOOKMARK_DEFAULT_FOLDER_IMMUTABLE(HttpStatus.BAD_REQUEST, "기본 폴더는 수정하거나 삭제할 수 없습니다."),

    // Reservation
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 예약입니다."),
    NOT_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "본인의 예약만 접근할 수 있습니다."),
    ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "이미 취소된 예약입니다."),

    // Remain
    REMAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 예약 시간대입니다."),
    REMAIN_EXHAUSTED(HttpStatus.BAD_REQUEST, "해당 시간대의 예약이 마감되었습니다."),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "이미 다른 사용자가 예약하여 마감되었습니다. 다시 시도해주세요."),

    // Review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 리뷰입니다."),
    NOT_REVIEW_OWNER(HttpStatus.FORBIDDEN, "본인의 리뷰만 접근할 수 있습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 해당 예약에 대한 리뷰를 작성했습니다."),
    REVIEW_NOT_ALLOWED(HttpStatus.FORBIDDEN, "예약이 방문 완료된 사용자만 리뷰를 작성할 수 있습니다."),

    // Chat
    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅 세션입니다."),
    CHAT_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "일일 메시지 제한(100회)을 초과했습니다."),
    CHAT_AI_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "AI 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    CHAT_AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 응답 시간이 초과되었습니다. 다시 시도해주세요."),
    CHAT_AI_AUTH_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 서비스 인증에 실패했습니다."),
    CHAT_AI_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 응답 생성 중 오류가 발생했습니다."),

    // Vacancy
    VACANCY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."),
    VACANCY_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 빈자리 알림을 등록했습니다."),
    VACANCY_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 알림입니다."),
    VACANCY_REMAIN_NOT_EXHAUSTED(HttpStatus.BAD_REQUEST, "아직 예약 가능한 시간대입니다. 잔여 좌석이 0일 때만 빈자리 알림을 등록할 수 있습니다."),

    // File
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 파일이 비어있습니다."),
    FILE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다. (jpg, png, webp만 가능)"),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기는 5MB를 초과할 수 없습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드 중 오류가 발생했습니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."),
    NOT_NOTIFICATION_OWNER(HttpStatus.FORBIDDEN, "본인의 알림만 접근할 수 있습니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제입니다."),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "결제 검증에 실패했습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
    PAYMENT_PORTONE_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "포트원 API 호출 중 오류가 발생했습니다."),
    PAYMENT_REFUND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "결제 환불 처리 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}