// 빈자리 알림 목록 조회 시 remain 정보 참조를 위해 임시로 구현해둠.
// store_remain 테이블 담당 팀원이 구현 시 이 파일을 대체할 것.
package com.catchtable.store.repository;

import com.catchtable.store.entity.StoreRemain;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRemainRepository extends JpaRepository<StoreRemain, Long> {
}
