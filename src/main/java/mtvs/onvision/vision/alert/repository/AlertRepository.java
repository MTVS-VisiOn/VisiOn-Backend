package mtvs.onvision.vision.alert.repository;

import mtvs.onvision.vision.alert.domain.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
}
