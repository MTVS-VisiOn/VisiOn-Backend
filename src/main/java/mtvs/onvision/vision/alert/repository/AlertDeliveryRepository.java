package mtvs.onvision.vision.alert.repository;

import mtvs.onvision.vision.alert.domain.AlertDelivery;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {
    List<AlertDelivery> findAllByAlertId(Long alertId);

    /** 재전송 대상. occurredAt이 임계값 안쪽인 것만 */
    @EntityGraph(attributePaths = "alert")
    List<AlertDelivery> findAllByStatusInAndAlertOccurredAtGreaterThanEqual(
            Collection<NotifyStatus> statuses, Instant from);

    /** 포기 대상. occurredAt이 임계값을 넘긴 것 */
    @EntityGraph(attributePaths = "alert")
    List<AlertDelivery> findAllByStatusInAndAlertOccurredAtLessThan(
            Collection<NotifyStatus> statuses, Instant from);
}
