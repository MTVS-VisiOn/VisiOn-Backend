package mtvs.onvision.vision.alert.repository;

import mtvs.onvision.vision.alert.domain.Alert;
import mtvs.onvision.vision.alert.domain.AlertDelivery;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.common.config.JpaConfig;
import mtvs.onvision.vision.support.PostgresContainerSupport;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파생 쿼리 이름(`...AndAlertOccurredAt...`)이 실제로 연관을 타고 들어가는지 확인한다.
 * 이름이 틀리면 부팅 시점에야 터지므로 여기서 먼저 잡는다.
 */
@DataJpaTest
@Import(JpaConfig.class)   // BaseEntity의 createdAt/updatedAt이 nullable = false다. 감사 설정이 필요하다
@DisplayName("AlertDeliveryRepository의")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertDeliveryRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private AlertDeliveryRepository alertDeliveryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final EnumSet<NotifyStatus> RETRY_TARGET =
            EnumSet.of(NotifyStatus.PENDING, NotifyStatus.FAILED);

    private Instant threshold() {
        return Instant.now().minus(Duration.ofMinutes(5));
    }

    private Alert persistAlert(Instant occurredAt) {
        Alert alert = new Alert(AlertType.OBSTACLE, "전방 2m에 자전거가 세워져 있습니다",
                37.4979, 127.0276, "서울특별시 강남구 테헤란로 152",
                "alerts/OBSTACLE/test.jpg", occurredAt, "우회 안내", null);
        return entityManager.persist(alert);
    }

    private AlertDelivery persistDelivery(Alert alert, String fid, NotifyStatus status) {
        AlertDelivery delivery = new AlertDelivery(alert, fid);
        if (status != NotifyStatus.PENDING) {
            delivery.markResult(status);
        }
        return entityManager.persist(delivery);
    }

    @Nested
    @DisplayName("Describe: findAllByStatusInAndAlertOccurredAtGreaterThanEqual 메서드는")
    class Describe_with_findRetryTargets {

        @Test
        @DisplayName("It : 임계값 안쪽의 PENDING·FAILED만 가져온다")
        void it_returns_only_recent_unsent() {
            //given
            Alert recent = persistAlert(Instant.now());
            Alert old = persistAlert(Instant.now().minus(Duration.ofMinutes(30)));

            persistDelivery(recent, "fid-pending", NotifyStatus.PENDING);
            persistDelivery(recent, "fid-failed", NotifyStatus.FAILED);
            persistDelivery(recent, "fid-sent", NotifyStatus.SENT);
            persistDelivery(recent, "fid-unregistered", NotifyStatus.UNREGISTERED);
            persistDelivery(old, "fid-old", NotifyStatus.PENDING);
            entityManager.flush();
            entityManager.clear();

            //when
            List<AlertDelivery> targets = alertDeliveryRepository
                    .findAllByStatusInAndAlertOccurredAtGreaterThanEqual(RETRY_TARGET, threshold());

            //then - SENT·UNREGISTERED는 재전송 대상이 아니고, 30분 전 건은 임계값 밖이다
            assertThat(targets)
                    .extracting(AlertDelivery::getFid)
                    .containsExactlyInAnyOrder("fid-pending", "fid-failed");
        }

        @Test
        @DisplayName("It : alert를 함께 가져온다 (N+1이 나지 않는다)")
        void it_fetches_alert_together() {
            //given
            Alert recent = persistAlert(Instant.now());
            persistDelivery(recent, "fid-pending", NotifyStatus.PENDING);
            entityManager.flush();
            entityManager.clear();

            //when
            List<AlertDelivery> targets = alertDeliveryRepository
                    .findAllByStatusInAndAlertOccurredAtGreaterThanEqual(RETRY_TARGET, threshold());

            //then - @EntityGraph가 빠지면 프록시 상태라 false가 된다
            assertThat(targets).hasSize(1);
            assertThat(Hibernate.isInitialized(targets.get(0).getAlert())).isTrue();
        }
    }

    @Nested
    @DisplayName("Describe: findAllByStatusInAndAlertOccurredAtLessThan 메서드는")
    class Describe_with_findExpireTargets {

        @Test
        @DisplayName("It : 임계값을 넘긴 PENDING·FAILED만 가져온다")
        void it_returns_only_old_unsent() {
            //given
            Alert recent = persistAlert(Instant.now());
            Alert old = persistAlert(Instant.now().minus(Duration.ofMinutes(30)));

            persistDelivery(recent, "fid-recent", NotifyStatus.PENDING);
            persistDelivery(old, "fid-old-pending", NotifyStatus.PENDING);
            persistDelivery(old, "fid-old-sent", NotifyStatus.SENT);
            entityManager.flush();
            entityManager.clear();

            //when
            List<AlertDelivery> targets = alertDeliveryRepository
                    .findAllByStatusInAndAlertOccurredAtLessThan(RETRY_TARGET, threshold());

            //then
            assertThat(targets)
                    .extracting(AlertDelivery::getFid)
                    .containsExactly("fid-old-pending");
        }
    }

    @Nested
    @DisplayName("Describe: findAllByAlertId 메서드는")
    class Describe_with_findAllByAlertId {

        @Test
        @DisplayName("It : 해당 알림의 발송 건만 가져온다")
        void it_returns_deliveries_of_the_alert() {
            //given
            Alert target = persistAlert(Instant.now());
            Alert other = persistAlert(Instant.now());

            persistDelivery(target, "fid-phone", NotifyStatus.PENDING);
            persistDelivery(target, "fid-tablet", NotifyStatus.PENDING);
            persistDelivery(other, "fid-phone", NotifyStatus.PENDING);
            entityManager.flush();
            entityManager.clear();

            //when
            List<AlertDelivery> deliveries = alertDeliveryRepository.findAllByAlertId(target.getId());

            //then - 같은 fid라도 알림이 다르면 섞이지 않는다
            assertThat(deliveries)
                    .extracting(AlertDelivery::getFid)
                    .containsExactlyInAnyOrder("fid-phone", "fid-tablet");
        }
    }
}
