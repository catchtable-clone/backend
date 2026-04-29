package com.catchtable.notification.repository;

import com.catchtable.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 사용자의 삭제되지 않은 알림 목록 조회
    Page<Notification> findByUserIdAndIsDeletedFalse(Long userId, Pageable pageable);

    // 읽지 않은 알림 개수 카운트
    long countByUserIdAndIsReadFalseAndIsDeletedFalse(Long userId);
}
