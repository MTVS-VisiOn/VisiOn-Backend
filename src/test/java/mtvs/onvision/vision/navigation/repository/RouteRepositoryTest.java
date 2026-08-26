package mtvs.onvision.vision.navigation.repository;

import mtvs.onvision.vision.common.config.JpaConfig;
import mtvs.onvision.vision.navigation.domain.Route;
import mtvs.onvision.vision.navigation.domain.RouteStatus;
import mtvs.onvision.vision.navigation.domain.TransportMode;
import mtvs.onvision.vision.navigation.dto.RouteSummary;
import mtvs.onvision.vision.navigation.dto.WalkSummaryResponse;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static mtvs.onvision.vision.common.util.AppTime.SEOUL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 레코드 프로젝션(`RouteSummary`)이 의도한 기간·정렬·필터로 동작하는지 확인한다.
 * 프로젝션이 풀리면 `Route.report`(경로 전체 JSON)까지 통째로 딸려 나오는데, 목록 API라 티가 안 난다.
 */
@DataJpaTest
@Import(JpaConfig.class)   // createdAt이 nullable = false다. 감사 설정이 필요하다
@DisplayName("RouteRepository의")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RouteRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email, String phoneNumber) {
        return entityManager.persist(new User(email, "password", "이름", phoneNumber, UserRole.WARD));
    }

    private WalkSummaryResponse summary(String destinationName) {
        return new WalkSummaryResponse(
                0, TransportMode.WALK,
                24269, 21600,
                44, 0, 1, 0,
                "신논현역", "서울 강남구 강남대로 지하 476", List.of(37.504585, 127.024798),
                destinationName, "서울 서초구 강남대로 213", List.of(37.479103, 127.037476));
    }

    /**
     * 과거 경로를 심는다.
     * <p>
     * `createdAt`은 `@CreatedDate`라 persist 시점에 감사 리스너가 덮어쓰고, `updatable = false`라
     * JPA UPDATE 문에서도 빠진다. 그래서 필드를 바꿔 flush해도 DB에 반영되지 않는다.
     * 네이티브 쿼리로 직접 넣어야 한다.
     */
    private void persistRouteAt(User ward, String destinationName, RouteStatus status, LocalDateTime createdAt) {
        Route route = new Route(TransportMode.WALK, summary(destinationName), "{}", ward);
        entityManager.persist(route);
        entityManager.flush();

        entityManager.getEntityManager()
                .createNativeQuery("update routes set created_at = ?1, status = ?2 where id = ?3")
                .setParameter(1, createdAt)
                .setParameter(2, status.name())
                .setParameter(3, route.getId())
                .executeUpdate();
        entityManager.clear();
    }

    @Nested
    @DisplayName("Describe: findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc 메서드는")
    class Describe_with_findAllSince {

        @Nested
        @DisplayName("Context: 기준 시각 앞뒤로 경로가 섞여 있으면")
        class Context_with_routes_around_boundary {

            @Test
            @DisplayName("It : 기준 시각 이후 경로만 가져온다")
            void it_filters_by_created_at() {
                //given
                User ward = persistUser("ward1@test.com", "01011110001");
                LocalDateTime from = LocalDate.now(SEOUL).minusDays(6).atStartOfDay();

                persistRouteAt(ward, "일주일 밖", RouteStatus.COMPLETED, from.minusDays(1));
                persistRouteAt(ward, "1초 전", RouteStatus.COMPLETED, from.minusSeconds(1));
                persistRouteAt(ward, "기준 정각", RouteStatus.COMPLETED, from);
                persistRouteAt(ward, "기준 이후", RouteStatus.COMPLETED, from.plusHours(1));

                //when
                List<RouteSummary> found = routeRepository
                        .findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(ward.getId(), from);

                //then : GreaterThanEqual이므로 기준 정각은 포함이다
                assertThat(found).extracting(RouteSummary::destinationName)
                        .containsExactly("기준 이후", "기준 정각");
            }

            @Test
            @DisplayName("It : createdAt 최신순으로 정렬한다")
            void it_sorts_by_created_at_desc() {
                //given : 입력 순서와 시각 순서를 어긋나게 심어야 정렬이 실제로 검증된다
                User ward = persistUser("ward2@test.com", "01011110002");
                LocalDateTime from = LocalDate.now(SEOUL).minusDays(6).atStartOfDay();

                persistRouteAt(ward, "두번째", RouteStatus.COMPLETED, from.plusDays(2));
                persistRouteAt(ward, "가장 최근", RouteStatus.COMPLETED, from.plusDays(3));
                persistRouteAt(ward, "가장 오래됨", RouteStatus.COMPLETED, from.plusDays(1));

                //when
                List<RouteSummary> found = routeRepository
                        .findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(ward.getId(), from);

                //then
                assertThat(found).extracting(RouteSummary::destinationName)
                        .containsExactly("가장 최근", "두번째", "가장 오래됨");
                assertThat(found).extracting(RouteSummary::createdAt)
                        .isSortedAccordingTo(Comparator.reverseOrder());
            }
        }

        @Nested
        @DisplayName("Context: 상태가 다른 경로가 섞여 있으면")
        class Context_with_mixed_status {

            @Test
            @DisplayName("It : 거르지 않고 status를 그대로 실어 준다")
            void it_returns_all_statuses_with_status_field() {
                //given
                User ward = persistUser("ward3@test.com", "01011110003");
                LocalDateTime from = LocalDate.now(SEOUL).minusDays(6).atStartOfDay();

                persistRouteAt(ward, "진행중", RouteStatus.IN_PROGRESS, from.plusDays(3));
                persistRouteAt(ward, "완료", RouteStatus.COMPLETED, from.plusDays(2));
                persistRouteAt(ward, "취소", RouteStatus.CANCELED, from.plusDays(1));

                //when
                List<RouteSummary> found = routeRepository
                        .findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(ward.getId(), from);

                //then
                assertThat(found).extracting(RouteSummary::status)
                        .containsExactly(RouteStatus.IN_PROGRESS, RouteStatus.COMPLETED, RouteStatus.CANCELED);
            }
        }

        @Nested
        @DisplayName("Context: 다른 피보호자의 경로가 섞여 있으면")
        class Context_with_other_ward {

            @Test
            @DisplayName("It : 요청한 피보호자의 경로만 가져온다")
            void it_filters_by_ward() {
                //given
                User ward = persistUser("ward4@test.com", "01011110004");
                User otherWard = persistUser("ward5@test.com", "01011110005");
                LocalDateTime from = LocalDate.now(SEOUL).minusDays(6).atStartOfDay();

                persistRouteAt(ward, "내 경로", RouteStatus.COMPLETED, from.plusDays(1));
                persistRouteAt(otherWard, "남의 경로 1", RouteStatus.COMPLETED, from.plusDays(1));
                persistRouteAt(otherWard, "남의 경로 2", RouteStatus.COMPLETED, from.plusDays(2));

                //when
                List<RouteSummary> found = routeRepository
                        .findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(ward.getId(), from);

                //then
                assertThat(found).hasSize(1);
                assertThat(found.getFirst().destinationName()).isEqualTo("내 경로");
            }
        }

        @Nested
        @DisplayName("Context: 기간 안에 경로가 없으면")
        class Context_without_route {

            @Test
            @DisplayName("It : 빈 목록을 반환한다")
            void it_returns_empty_list() {
                //given
                User ward = persistUser("ward6@test.com", "01011110006");
                entityManager.flush();

                //when&then
                assertThat(routeRepository.findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        ward.getId(), LocalDate.now(SEOUL).minusDays(6).atStartOfDay())).isEmpty();
            }
        }
    }
}
