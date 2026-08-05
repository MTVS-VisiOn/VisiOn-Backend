package mtvs.onvision.vision.alert.repository;

import mtvs.onvision.vision.alert.domain.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findAllBySenderIdAndOccurredAtAfterOrderByOccurredAtDesc(Long senderId, Instant occurredAt);
}
