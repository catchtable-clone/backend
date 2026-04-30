package com.catchtable.notification.repository;

import com.catchtable.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 사용자의 삭제되지 않은 알림 목록 조회
    Page<Notification> findByUserIdAndIsDeletedFalse(Long userId, Pageable pageable);

    // 읽지 않은 알림 개수 카운트
    long countByUserIdAndIsReadFalseAndIsDeletedFalse(Long userId);

    // 사용자의 모든 알림을 읽음 처리하는 벌크 업데이트 쿼리
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);
}
