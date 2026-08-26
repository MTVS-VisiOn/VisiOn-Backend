package mtvs.onvision.vision.location.repository;

import mtvs.onvision.vision.common.config.JpaConfig;
import mtvs.onvision.vision.location.domain.LocationHistory;
import mtvs.onvision.vision.location.domain.MovementStatus;
import mtvs.onvision.vision.location.dto.LocationReport;
import mtvs.onvision.vision.support.PostgresContainerSupport;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `ON CONFLICT DO NOTHING`이 실제로 중복을 흡수하는지 확인한다.
 * <p>
 * 재시도가 정상 경로라(LMOVE + 커밋 후 삭제) 이 동작이 깨지면 유니크 제약 위반으로
 * 배치 전체가 롤백되고, 버퍼가 영영 안 비워진다. 조용히 밀리는 종류의 고장이다.
 */
@DataJpaTest
@Import({JpaConfig.class, LocationHistoryJdbcRepository.class})
@DisplayName("LocationHistoryJdbcRepository의")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LocationHistoryJdbcRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private LocationHistoryJdbcRepository locationHistoryJdbcRepository;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final Instant BASE = Instant.parse("2026-08-11T05:00:00Z");

    private User persistWard(String email, String phoneNumber) {
        User ward = entityManager.persist(new User(email, "password", "피보호자", phoneNumber, UserRole.WARD));
        entityManager.flush();
        return ward;
    }

    /** 전송 간격이 3초라 실제 데이터와 같은 간격으로 만든다 */
    private LocationReport report(Long wardId, int seq) {
        return new LocationReport(wardId, 37.413196 + seq * 0.0001, 127.098403,
                12.5f, MovementStatus.ON_FOOT, BASE.plusSeconds(seq * 3L));
    }

    @Nested
    @DisplayName("Describe: batchInsert 메서드는")
    class Describe_with_batchInsert {

        @Nested
        @DisplayName("Context: 새 데이터만 주어지면")
        class Context_with_new_rows {

            @Test
            @DisplayName("It : 전부 저장하고 건수를 돌려준다")
            void it_inserts_all() {
                //given
                User ward = persistWard("ward1@test.com", "01011110001");
                List<LocationReport> reports = List.of(report(ward.getId(), 0), report(ward.getId(), 1));

                //when
                int inserted = locationHistoryJdbcRepository.batchInsert(reports);

                //then
                assertThat(inserted).isEqualTo(2);
                assertThat(locationHistoryRepository.findAll()).hasSize(2);
            }

            @Test
            @DisplayName("(TIMESTAMP WITHOUT TIME ZONE)It : recordedAt을 UTC로 넣어 Instant로 되읽는다")
            void it_roundtrips_recorded_at() {
                //given : JDBC가 JVM 시간대로 넣으면 Hibernate가 읽을 때 9시간 어긋난다
                User ward = persistWard("ward2@test.com", "01011110002");

                //when
                locationHistoryJdbcRepository.batchInsert(List.of(report(ward.getId(), 0)));

                //then
                LocationHistory saved = locationHistoryRepository.findAll().getFirst();
                assertThat(saved.getRecordedAt()).isEqualTo(BASE);
                assertThat(saved.getStatus()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.getAccuracy()).isEqualTo(12.5f);
            }

            @Test
            @DisplayName("(accuracy가 null)It : 예외 없이 저장한다")
            void it_accepts_null_accuracy() {
                //given : setObject에 타입을 안 주면 드라이버가 타입을 몰라 터진다
                User ward = persistWard("ward3@test.com", "01011110003");
                LocationReport noAccuracy = new LocationReport(ward.getId(), 37.413196, 127.098403,
                        null, MovementStatus.UNKNOWN, BASE);

                //when
                int inserted = locationHistoryJdbcRepository.batchInsert(List.of(noAccuracy));

                //then
                assertThat(inserted).isEqualTo(1);
                assertThat(locationHistoryRepository.findAll().getFirst().getAccuracy()).isNull();
            }
        }

        @Nested
        @DisplayName("Context: 이미 저장된 데이터가 다시 들어오면")
        class Context_with_duplicates {

            @Test
            @DisplayName("It : 예외 없이 건너뛰고 행이 늘지 않는다")
            void it_skips_duplicates() {
                //given
                User ward = persistWard("ward4@test.com", "01011110004");
                List<LocationReport> reports = List.of(report(ward.getId(), 0), report(ward.getId(), 1));
                locationHistoryJdbcRepository.batchInsert(reports);

                //when : 커밋 후 처리 중 큐를 지우기 전에 죽으면 이 배치가 통째로 재시도된다
                int inserted = locationHistoryJdbcRepository.batchInsert(reports);

                //then
                assertThat(inserted).isZero();
                assertThat(locationHistoryRepository.findAll()).hasSize(2);
            }

            @Test
            @DisplayName("(새 것과 섞여 있을때)It : 새 것만 넣고 나머지는 건너뛴다")
            void it_inserts_only_new_ones() {
                //given
                User ward = persistWard("ward5@test.com", "01011110005");
                locationHistoryJdbcRepository.batchInsert(List.of(report(ward.getId(), 0)));

                //when : 중복 1건 + 새것 2건. 유니크 위반으로 배치 전체가 롤백되면 안 된다
                int inserted = locationHistoryJdbcRepository.batchInsert(
                        List.of(report(ward.getId(), 0), report(ward.getId(), 1), report(ward.getId(), 2)));

                //then
                assertThat(inserted).isEqualTo(2);
                assertThat(locationHistoryRepository.findAll()).hasSize(3);
            }

            @Test
            @DisplayName("(피보호자가 다르면)It : 같은 시각이어도 별개로 저장한다")
            void it_separates_by_ward() {
                //given : 유니크 키가 (ward_id, recorded_at)이라 피보호자가 다르면 충돌이 아니다
                User ward = persistWard("ward6@test.com", "01011110006");
                User otherWard = persistWard("ward7@test.com", "01011110007");

                //when
                int inserted = locationHistoryJdbcRepository.batchInsert(
                        List.of(report(ward.getId(), 0), report(otherWard.getId(), 0)));

                //then
                assertThat(inserted).isEqualTo(2);
            }
        }

        @Nested
        @DisplayName("Context: 빈 목록이 주어지면")
        class Context_with_empty_list {

            @Test
            @DisplayName("It : 0을 돌려주고 쿼리를 보내지 않는다")
            void it_returns_zero() {
                assertThat(locationHistoryJdbcRepository.batchInsert(List.of())).isZero();
            }
        }
    }
}
