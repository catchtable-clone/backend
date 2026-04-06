// 빈자리 알림 목록 조회 시 remainDate, remainTime, storeId 참조를 위해 임시로 구현해둠.
// store_remain 테이블 담당 팀원이 구현 시 이 파일을 대체할 것.
package com.catchtable.remain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "store_remain")
public class StoreRemain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "remain_date", nullable = false)
    private LocalDate remainDate;

    @Column(name = "remain_time", nullable = false)
    private LocalTime remainTime;

    @Column(name = "remain_team", nullable = false)
    private Long remainTeam;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
