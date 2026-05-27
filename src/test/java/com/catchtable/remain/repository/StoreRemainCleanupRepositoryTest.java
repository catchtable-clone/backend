package com.catchtable.remain.repository;

import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.store.entity.Category;
import com.catchtable.store.entity.District;
import com.catchtable.store.entity.Store;
import com.catchtable.user.entity.User;
import com.catchtable.vacancy.entity.Vacancy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StoreRemainRepository.deleteUnreferencedPastSlots 의 핵심 계약을 검증하는 통합 테스트.
 *
 * 회귀 방지 포인트(실제 운영 사고에서 학습):
 *  - 지난 날짜 슬롯이라도 reservation / vacancy_subscriptions 가 참조하면 삭제하면 안 된다.
 *    (참조 슬롯 삭제 시 payment→reservation→store_remain FK 위반 발생)
 *  - 미래 슬롯은 절대 삭제하면 안 된다.
 *  - LIMIT(batchSize) 만큼만 삭제해, 대량 삭제로 인한 WAL 폭증을 막는다.
 *
 * 네이티브 PostgreSQL 쿼리(LIMIT 서브쿼리 + NOT EXISTS)라 실제 Postgres에서만 의미가 있어
 * Testcontainers(postgres:16-alpine)로 검증한다. (로컬/CI에 Docker 필요)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StoreRemainCleanupRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // application.yaml 의 무-기본값 placeholder 방어용 더미 (슬라이스에선 사용 안 되지만 안전망)
        registry.add("GEMINI_API_KEY", () -> "test");
        registry.add("GOOGLE_CLIENT_ID", () -> "test");
        registry.add("GOOGLE_CLIENT_SECRET", () -> "test");
    }

    @Autowired
    private StoreRemainRepository storeRemainRepository;

    @Autowired
    private TestEntityManager em;

    private static final LocalDate TODAY = LocalDate.now();

    private Store store;
    private User user;

    @BeforeEach
    void setUp() {
        store = em.persist(Store.builder()
                .storeName("테스트매장")
                .category(Category.WESTERN)
                .latitude(37.534).longitude(126.993)
                .address("서울 용산구 이태원로 246")
                .district(District.YONGSAN)
                .team(10)
                .openTime("11:00").closeTime("22:00")
                .build());
        user = em.persist(User.builder()
                .email("tester@catchtable.com")
                .nickname("테스터")
                .googleId("google-test-1")
                .build());
        em.flush();
    }

    private StoreRemain persistSlot(LocalDate date) {
        return em.persist(StoreRemain.builder()
                .store(store)
                .remainDate(date)
                .remainTime(LocalTime.of(12, 0))
                .remainTeam(10)
                .build());
    }

    @Test
    @DisplayName("과거 미참조 슬롯만 삭제하고, 참조 슬롯(예약/빈자리구독)·미래 슬롯은 보존한다")
    void deletesOnlyUnreferencedPastSlots() {
        // given
        StoreRemain pastUnreferenced = persistSlot(TODAY.minusDays(10)); // 삭제 대상
        StoreRemain pastRefByReservation = persistSlot(TODAY.minusDays(11)); // 예약 참조 → 보존
        StoreRemain pastRefByVacancy = persistSlot(TODAY.minusDays(12)); // 빈자리 구독 참조 → 보존
        StoreRemain futureUnreferenced = persistSlot(TODAY.plusDays(10)); // 미래 → 보존

        em.persist(Reservation.builder()
                .user(user)
                .storeRemain(pastRefByReservation)
                .coupon(null)
                .member(2)
                .status(ReservationStatus.CONFIRMED)
                .build());
        em.persist(new Vacancy(user, pastRefByVacancy));
        em.flush();
        em.clear();

        // when
        int deleted = storeRemainRepository.deleteUnreferencedPastSlots(TODAY, 1000);

        // then
        em.clear();
        assertThat(deleted).isEqualTo(1);
        assertThat(storeRemainRepository.findById(pastUnreferenced.getId())).isEmpty();
        assertThat(storeRemainRepository.findById(pastRefByReservation.getId())).isPresent();
        assertThat(storeRemainRepository.findById(pastRefByVacancy.getId())).isPresent();
        assertThat(storeRemainRepository.findById(futureUnreferenced.getId())).isPresent();
    }

    @Test
    @DisplayName("batchSize 만큼만 삭제하며, 반복 호출로 모든 과거 미참조 슬롯이 정리된다")
    void respectsBatchSizeAndDrainsOverRepeatedCalls() {
        // given: 과거 미참조 슬롯 5개
        for (int i = 0; i < 5; i++) {
            persistSlot(TODAY.minusDays(1 + i));
        }
        em.flush();
        em.clear();

        // when: 배치 크기 2로 반복 호출
        int first = storeRemainRepository.deleteUnreferencedPastSlots(TODAY, 2);
        int second = storeRemainRepository.deleteUnreferencedPastSlots(TODAY, 2);
        int third = storeRemainRepository.deleteUnreferencedPastSlots(TODAY, 2);
        int fourth = storeRemainRepository.deleteUnreferencedPastSlots(TODAY, 2);

        // then: 2 + 2 + 1 + 0 = 5
        assertThat(first).isEqualTo(2);
        assertThat(second).isEqualTo(2);
        assertThat(third).isEqualTo(1);
        assertThat(fourth).isZero();
    }
}
