package com.catchtable.buddy.repository;

import com.catchtable.buddy.entity.Buddy;
import com.catchtable.buddy.entity.BuddyStatus;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BuddyRepository extends JpaRepository<Buddy, Long> {

    boolean existsByRequesterIdAndReceiverIdAndStatusIn(Long requesterId, Long receiverId, List<BuddyStatus> statuses);

    List<Buddy> findByReceiverIdAndStatus(Long receiverId, BuddyStatus status);

    @Query("""
            SELECT b FROM Buddy b
            WHERE b.status = 'ACCEPTED'
              AND (b.requester.id = :userId OR b.receiver.id = :userId)
            """)
    List<Buddy> findAcceptedBuddiesByUserId(@Param("userId") Long userId);

    default Buddy getById(Long id) {
        return findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.BUDDY_REQUEST_NOT_FOUND));
    }
}
